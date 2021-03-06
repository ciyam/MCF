package org.qora.controller;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.block.Block;
import org.qora.block.Block.ValidationResult;
import org.qora.block.BlockChain;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockSummaryData;
import org.qora.data.transaction.TransactionData;
import org.qora.network.Peer;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.BlockSummariesMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetBlockSummariesMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.GetSignaturesV2Message;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageType;
import org.qora.network.message.SignaturesMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.transaction.Transaction;
import org.qora.utils.Base58;

public class Synchronizer {

	private static final Logger LOGGER = LogManager.getLogger(Synchronizer.class);

	private static final int INITIAL_BLOCK_STEP = 8;
	private static final int MAXIMUM_BLOCK_STEP = 500;
	private static final int MAXIMUM_HEIGHT_DELTA = 300; // XXX move to blockchain config?
	private static final int MAXIMUM_COMMON_DELTA = 60; // XXX move to blockchain config?
	private static final int SYNC_BATCH_SIZE = 200;

	private static final List<byte[]> BANNED_BLOCK_SIGNATURES = Arrays.asList(
		BlockChain.CANCEL_ASSET_ORDER_BLOCK_SIG,
		BlockChain.FORKED_BLOCK_44020_SIG
	);

	private static Synchronizer instance;

	private Repository repository;

	public enum SynchronizationResult {
		OK, NOTHING_TO_DO, GENESIS_ONLY, NO_COMMON_BLOCK, TOO_FAR_BEHIND, TOO_DIVERGENT, NO_REPLY, INFERIOR_CHAIN, INVALID_DATA, NO_BLOCKCHAIN_LOCK, REPOSITORY_ISSUE;
	}

	// Constructors

	private Synchronizer() {
	}

	public static Synchronizer getInstance() {
		if (instance == null)
			instance = new Synchronizer();

		return instance;
	}

	/**
	 * Attempt to synchronize blockchain with peer.
	 * <p>
	 * Will return <tt>true</tt> if synchronization succeeded,
	 * even if no changes were made to our blockchain.
	 * <p>
	 * @param peer
	 * @return false if something went wrong, true otherwise.
	 * @throws InterruptedException
	 */
	public SynchronizationResult synchronize(Peer peer, boolean force) throws InterruptedException {
		// Make sure we're the only thread modifying the blockchain
		// If we're already synchronizing with another peer then this will also return fast
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (!blockchainLock.tryLock())
			// Wasn't peer's fault we couldn't sync
			return SynchronizationResult.NO_BLOCKCHAIN_LOCK;

		try {
			try (final Repository repository = RepositoryManager.getRepository()) {
				try {
					this.repository = repository;
					final BlockData ourLatestBlockData = this.repository.getBlockRepository().getLastBlock();
					final int ourInitialHeight = ourLatestBlockData.getHeight();
					int ourHeight = ourInitialHeight;

					int peerHeight;
					byte[] peersLastBlockSignature;

					ReentrantLock peerLock = peer.getPeerDataLock();
					peerLock.lockInterruptibly();
					try {
						peerHeight = peer.getLastHeight();
						peersLastBlockSignature = peer.getLastBlockSignature();
					} finally {
						peerLock.unlock();
					}

					// If peer is at genesis block then peer has no blocks so ignore them for a while
					if (peerHeight == 1)
						return SynchronizationResult.GENESIS_ONLY;

					// If peer is too far behind us then don't them.
					int minHeight = ourHeight - MAXIMUM_HEIGHT_DELTA;
					if (!force && peerHeight < minHeight) {
						LOGGER.info(String.format("Peer %s height %d is too far behind our height %d", peer, peerHeight, ourHeight));
						return SynchronizationResult.TOO_FAR_BEHIND;
					}

					byte[] ourLastBlockSignature = ourLatestBlockData.getSignature();
					LOGGER.debug(String.format("Synchronizing with peer %s at height %d, sig %.8s, ts %d; our height %d, sig %.8s, ts %d", peer,
							peerHeight, Base58.encode(peersLastBlockSignature), peer.getLastBlockTimestamp(),
							ourHeight, Base58.encode(ourLastBlockSignature), ourLatestBlockData.getTimestamp()));

					List<byte[]> signatures = findSignaturesFromCommonBlock(peer, ourHeight);
					if (signatures == null) {
						LOGGER.info(String.format("Error while trying to find common block with peer %s", peer));
						return SynchronizationResult.NO_REPLY;
					}
					if (signatures.isEmpty()) {
						LOGGER.info(String.format("Failure to find common block with peer %s", peer));
						return SynchronizationResult.NO_COMMON_BLOCK;
					}

					// First signature is common block
					BlockData commonBlockData = this.repository.getBlockRepository().fromSignature(signatures.get(0));
					final int commonBlockHeight = commonBlockData.getHeight();
					LOGGER.debug(String.format("Common block with peer %s is at height %d, sig %.8s, ts %d", peer,
							commonBlockHeight, Base58.encode(commonBlockData.getSignature()), commonBlockData.getTimestamp()));
					signatures.remove(0);

					// If common block height is higher than peer's last reported height
					// then peer must have a very recent sync. Update our idea of peer's height.
					if (commonBlockHeight > peerHeight) {
						LOGGER.debug(String.format("Peer height %d was lower than common block height %d - using higher value", peerHeight, commonBlockHeight));
						peerHeight = commonBlockHeight;
					}

					// If common block is peer's latest block then we simply have the same, or longer, chain to peer, so exit now
					if (commonBlockHeight == peerHeight) {
						if (peerHeight == ourHeight)
							LOGGER.debug(String.format("We have the same blockchain as peer %s", peer));
						else
							LOGGER.debug(String.format("We have the same blockchain as peer %s, but longer", peer));

						return SynchronizationResult.NOTHING_TO_DO;
					}

					// If common block is too far behind us then we're on massively different forks so give up.
					int minCommonHeight = ourHeight - MAXIMUM_COMMON_DELTA;
					if (!force && commonBlockHeight < minCommonHeight) {
						LOGGER.info(String.format("Blockchain too divergent with peer %s", peer));
						return SynchronizationResult.TOO_DIVERGENT;
					}

					// If we both have blocks after common block then decide whether we want to sync
					int highestMutualHeight = Math.min(peerHeight, ourHeight);

					// If our latest block is very old, we're very behind and should ditch our fork.
					final Long minLatestBlockTimestamp = Controller.getMinimumLatestBlockTimestamp();
					if (minLatestBlockTimestamp == null)
						return SynchronizationResult.REPOSITORY_ISSUE;

					if (ourInitialHeight > commonBlockHeight && ourLatestBlockData.getTimestamp() < minLatestBlockTimestamp) {
						LOGGER.info(String.format("Ditching our chain after height %d as our latest block is very old", commonBlockHeight));
						highestMutualHeight = commonBlockHeight;
					}

					if (!force && highestMutualHeight > commonBlockHeight) {
						int numberRequired = highestMutualHeight - commonBlockHeight;

						LOGGER.debug(String.format("Comparing blocks %d to %d with peer %s", commonBlockHeight + 1, highestMutualHeight, peer));

						// We need block summaries (which we also use to fill signatures list)
						byte[] previousSignature = commonBlockData.getSignature();
						List<BlockSummaryData> peerBlockSummaries = new ArrayList<>();

						while (peerBlockSummaries.size() < numberRequired) {
							int height = commonBlockHeight + peerBlockSummaries.size();

							List<BlockSummaryData> moreBlockSummaries = this.getBlockSummaries(peer, previousSignature, numberRequired - peerBlockSummaries.size());

							if (moreBlockSummaries == null || moreBlockSummaries.isEmpty()) {
								LOGGER.info(String.format("Peer %s failed to respond with block summaries after height %d, sig %.8s", peer,
										height, Base58.encode(previousSignature)));
								return SynchronizationResult.NO_REPLY;
							}

							// Check peer sent valid heights
							for (int i = 0; i < moreBlockSummaries.size(); ++i) {
								++height;

								BlockSummaryData blockSummary = moreBlockSummaries.get(i);

								if (blockSummary.getHeight() != height) {
									LOGGER.info(String.format("Peer %s responded with invalid block summary for height %d, sig %.8s", peer,
											height, Base58.encode(blockSummary.getSignature())));
									return SynchronizationResult.NO_REPLY;
								}
							}

							peerBlockSummaries.addAll(moreBlockSummaries);
						}

						// Fetch our corresponding block summaries
						List<BlockSummaryData> ourBlockSummaries = repository.getBlockRepository().getBlockSummaries(commonBlockHeight + 1, highestMutualHeight);

						// We only need to pass the same number of peer's block summaries, regardless of how many they sent
						peerBlockSummaries.subList(ourBlockSummaries.size(), peerBlockSummaries.size()).clear();

						// Calculate total 'distance' of both blockchain subsets, from common block to highest mutual block.
						BlockSummaryData parentBlockSummary = new BlockSummaryData(commonBlockData);
						List<List<BlockSummaryData>> comparableBlockSummaries = Arrays.asList(ourBlockSummaries, peerBlockSummaries);
						List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, comparableBlockSummaries);

						BigInteger ourBlockchainValue = distances.get(0);
						BigInteger peerBlockchainValue = distances.get(1);

						// If our blockchain has a lower distance then don't synchronize with peer
						if (ourBlockchainValue.compareTo(peerBlockchainValue) < 0) {
							LOGGER.debug(String.format("Not synchronizing with peer %s as we have better blockchain", peer));
							NumberFormat formatter = new DecimalFormat("0.###E0");
							LOGGER.debug(String.format("Our distance: %s, peer's distance: %s (lower is better)", formatter.format(ourBlockchainValue), formatter.format(peerBlockchainValue)));
							return SynchronizationResult.INFERIOR_CHAIN;
						}
					}

					if (ourHeight > commonBlockHeight) {
						// Unwind to common block (unless common block is our latest block)
						LOGGER.debug(String.format("Orphaning blocks back to height %d", commonBlockHeight));

						while (ourHeight > commonBlockHeight) {
							BlockData blockData = repository.getBlockRepository().fromHeight(ourHeight);
							Block block = new Block(repository, blockData);
							block.orphan();

							--ourHeight;
						}

						LOGGER.debug(String.format("Orphaned blocks back to height %d - fetching blocks from peer", commonBlockHeight, peer));
					} else {
						LOGGER.debug(String.format("Fetching new blocks from peer %s", peer));
					}

					// Fetch, and apply, blocks from peer
					byte[] signature = commonBlockData.getSignature();
					int maxBatchHeight = commonBlockHeight + SYNC_BATCH_SIZE;
					while (ourHeight < peerHeight && ourHeight < maxBatchHeight) {
						// Do we need more signatures?
						if (signatures.isEmpty()) {
							int numberRequested = maxBatchHeight - ourHeight;
							LOGGER.trace(String.format("Requesting %d signature%s after height %d", numberRequested, (numberRequested != 1 ? "s": ""), ourHeight));

							signatures = this.getBlockSignatures(peer, signature, numberRequested);

							if (signatures == null || signatures.isEmpty()) {
								LOGGER.info(String.format("Peer %s failed to respond with more block signatures after height %d, sig %.8s", peer,
										ourHeight, Base58.encode(signature)));
								return SynchronizationResult.NO_REPLY;
							}

							LOGGER.trace(String.format("Received %s signature%s", signatures.size(), (signatures.size() != 1 ? "s" : "")));
						}

						signature = signatures.get(0);
						signatures.remove(0);
						++ourHeight;

						// Is signature in our banned list?
						for (byte[] bannedSignature : BANNED_BLOCK_SIGNATURES)
							if (Arrays.equals(signature, bannedSignature)) {
								LOGGER.info(String.format("Peer %s sent banned block %.8s for height %d", peer,
										Base58.encode(signature), ourHeight));
								return SynchronizationResult.INFERIOR_CHAIN;
							}

						Block newBlock = this.fetchBlock(repository, peer, signature);

						if (newBlock == null) {
							LOGGER.info(String.format("Peer %s failed to respond with block for height %d, sig %.8s", peer,
									ourHeight, Base58.encode(signature)));
							return SynchronizationResult.NO_REPLY;
						}

						if (!newBlock.isSignatureValid()) {
							LOGGER.info(String.format("Peer %s sent block with invalid signature for height %d, sig %.8s", peer,
									ourHeight, Base58.encode(signature)));
							return SynchronizationResult.INVALID_DATA;
						}

						// Transactions are transmitted without approval status so determine that now
						for (Transaction transaction : newBlock.getTransactions())
							transaction.setInitialApprovalStatus();

						ValidationResult blockResult = newBlock.isValid();
						if (blockResult != ValidationResult.OK) {
							LOGGER.info(String.format("Peer %s sent invalid block for height %d, sig %.8s: %s", peer,
									ourHeight, Base58.encode(signature), blockResult.name()));
							return SynchronizationResult.INVALID_DATA;
						}

						// Save transactions attached to this block
						for (Transaction transaction : newBlock.getTransactions()) {
							TransactionData transactionData = transaction.getTransactionData();
							repository.getTransactionRepository().save(transactionData);
						}

						newBlock.process();

						// If we've grown our blockchain then at least save progress so far
						if (ourHeight > ourInitialHeight)
							repository.saveChanges();
					}

					// Commit
					repository.saveChanges();

					final BlockData newLatestBlockData = this.repository.getBlockRepository().getLastBlock();
					LOGGER.info(String.format("Synchronized with peer %s to height %d, sig %.8s, ts: %d", peer,
							newLatestBlockData.getHeight(), Base58.encode(newLatestBlockData.getSignature()),
							newLatestBlockData.getTimestamp()));

					return SynchronizationResult.OK;
				} finally {
					repository.discardChanges(); // Free repository locks, if any, also in case anything went wrong
					this.repository = null;
				}
			} catch (DataException e) {
				LOGGER.error("Repository issue during synchronization with peer", e);
				return SynchronizationResult.REPOSITORY_ISSUE;
			}
		} finally {
			blockchainLock.unlock();
		}
	}

	/**
	 * Returns list of peer's block signatures starting with common block with peer.
	 * 
	 * @param peer
	 * @return block signatures, or empty list if no common block, or null if there was an issue
	 * @throws DataException
	 * @throws InterruptedException
	 */
	private List<byte[]> findSignaturesFromCommonBlock(Peer peer, int ourHeight) throws DataException, InterruptedException {
		// Start by asking for a few recent block hashes as this will cover a majority of reorgs
		// Failing that, back off exponentially
		int step = INITIAL_BLOCK_STEP;

		List<byte[]> blockSignatures = null;
		int testHeight = Math.max(ourHeight - step, 1);
		byte[] testSignature = null;

		while (testHeight >= 1) {
			// Fetch our block signature at this height
			BlockData testBlockData = this.repository.getBlockRepository().fromHeight(testHeight);
			if (testBlockData == null) {
				// Not found? But we've locked the blockchain and height is below blockchain's tip!
				LOGGER.error("Failed to get block at height lower than blockchain tip during synchronization?");
				return null;
			}

			testSignature = testBlockData.getSignature();

			// Ask for block signatures since test block's signature
			LOGGER.trace(String.format("Requesting %d signature%s after height %d", step, (step != 1 ? "s": ""), testHeight));
			blockSignatures = this.getBlockSignatures(peer, testSignature, step);

			if (blockSignatures == null)
				// No response - give up this time
				return null;

			LOGGER.trace(String.format("Received %s signature%s", blockSignatures.size(), (blockSignatures.size() != 1 ? "s" : "")));

			// Empty list means remote peer is unaware of test signature OR has no new blocks after test signature
			if (!blockSignatures.isEmpty())
				// We have entries so we have found a common block
				break;

			// No blocks after genesis block?
			// We don't get called for a peer at genesis height so this means NO blocks in common
			if (testHeight == 1)
				return Collections.emptyList();

			if (peer.getVersion() >= 2) {
				step <<= 1;
			} else {
				// Old v1 peers are hard-coded to return 500 signatures so we might as well go backward by 500 too
				step = 500;
			}
			step = Math.min(step, MAXIMUM_BLOCK_STEP);

			testHeight = Math.max(testHeight - step, 1);
		}

		// Prepend common block's signature as first block sig
		blockSignatures.add(0, testSignature);

		// Work through returned signatures to get closer common block
		// Do this by trimming all-but-one leading known signatures
		for (int i = blockSignatures.size() - 1; i > 0; --i) {
			BlockData blockData = this.repository.getBlockRepository().fromSignature(blockSignatures.get(i));

			if (blockData != null) {
				// Note: index i isn't cleared: List.subList is fromIndex inclusive to toIndex exclusive
				blockSignatures.subList(0, i).clear();
				break;
			}
		}

		return blockSignatures;
	}

	private List<BlockSummaryData> getBlockSummaries(Peer peer, byte[] parentSignature, int numberRequested) throws InterruptedException {
		Message getBlockSummariesMessage = new GetBlockSummariesMessage(parentSignature, numberRequested);

		Message message = peer.getResponse(getBlockSummariesMessage);
		if (message == null || message.getType() != MessageType.BLOCK_SUMMARIES)
			return null;

		BlockSummariesMessage blockSummariesMessage = (BlockSummariesMessage) message;

		return blockSummariesMessage.getBlockSummaries();
	}

	private List<byte[]> getBlockSignatures(Peer peer, byte[] parentSignature, int numberRequested) throws InterruptedException {
		// numberRequested is v2+ feature
		Message getSignaturesMessage = peer.getVersion() >= 2 ? new GetSignaturesV2Message(parentSignature, numberRequested) : new GetSignaturesMessage(parentSignature);

		Message message = peer.getResponse(getSignaturesMessage);
		if (message == null || message.getType() != MessageType.SIGNATURES)
			return null;

		SignaturesMessage signaturesMessage = (SignaturesMessage) message;

		return signaturesMessage.getSignatures();
	}

	private Block fetchBlock(Repository repository, Peer peer, byte[] signature) throws InterruptedException {
		Message getBlockMessage = new GetBlockMessage(signature);

		Message message = peer.getResponse(getBlockMessage);
		if (message == null || message.getType() != MessageType.BLOCK)
			return null;

		BlockMessage blockMessage = (BlockMessage) message;

		try {
			return new Block(repository, blockMessage.getBlockData(), blockMessage.getTransactions(), blockMessage.getAtStates());
		} catch (DataException e) {
			LOGGER.debug("Failed to create block", e);
			return null;
		}
	}

}
