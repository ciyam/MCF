package org.qora.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.block.Block;
import org.qora.block.Block.ValidationResult;
import org.qora.block.GenesisBlock;
import org.qora.data.block.BlockData;
import org.qora.network.Peer;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageType;
import org.qora.network.message.SignaturesMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;

public class Synchronizer {

	private static final Logger LOGGER = LogManager.getLogger(Synchronizer.class);

	private static final int INITIAL_BLOCK_STEP = 8;
	private static final int MAXIMUM_BLOCK_STEP = 500;
	private static final int MAXIMUM_HEIGHT_DELTA = 2000; // XXX move to blockchain config?

	private static Synchronizer instance;

	private Repository repository;
	private int ourHeight;

	private Synchronizer() {
	}

	public static Synchronizer getInstance() {
		if (instance == null)
			instance = new Synchronizer();

		return instance;
	}

	public boolean synchronize(Peer peer) {
		// Make sure we're the only thread modifying the blockchain
		// If we're already synchronizing with another peer then this will also return fast
		Lock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (blockchainLock.tryLock())
			try {
				try (final Repository repository = RepositoryManager.getRepository()) {
					try {
						this.repository = repository;
						this.ourHeight = this.repository.getBlockRepository().getBlockchainHeight();
						int peerHeight = peer.getPeerData().getLastHeight();

						LOGGER.info(String.format("Synchronizing with peer %s from height %d to height %d", peer, this.ourHeight, peerHeight));

						List<byte[]> signatures = findSignaturesFromCommonBlock(peer);
						if (signatures == null) {
							LOGGER.info(String.format("Failure to find common block with peer %s", peer));
							return false;
						}

						// First signature is common block
						BlockData commonBlockData = this.repository.getBlockRepository().fromSignature(signatures.get(0));
						signatures.remove(0);

						// If common block is too far behind us then we're on massively different forks so give up.
						int minHeight = ourHeight - MAXIMUM_HEIGHT_DELTA;
						if (commonBlockData.getHeight() < minHeight) {
							LOGGER.info(String.format("Blockchain too divergent with peer %s", peer));
							return false;
						}

						if (this.ourHeight > commonBlockData.getHeight()) {
							// Unwind to common block (unless common block is our latest block)
							LOGGER.debug(String.format("Orphaning blocks back to height %d", commonBlockData.getHeight()));

							while (this.ourHeight > commonBlockData.getHeight()) {
								BlockData blockData = repository.getBlockRepository().fromHeight(this.ourHeight);
								Block block = new Block(repository, blockData);
								block.orphan();

								--this.ourHeight;
							}

							LOGGER.debug(String.format("Orphaned blocks back to height %d - fetching blocks from peer", commonBlockData.getHeight(), peer));
						} else {
							LOGGER.debug(String.format("Fetching new blocks from peer %s", peer));
						}

						// Fetch, and apply, blocks from peer
						byte[] signature = commonBlockData.getSignature();
						while (this.ourHeight < peerHeight) {
							// Do we need more signatures?
							if (signatures.isEmpty()) {
								signatures = this.getBlockSignatures(peer, signature, MAXIMUM_BLOCK_STEP);
								if (signatures == null || signatures.isEmpty()) {
									LOGGER.info(String.format("Peer %s failed to respond with more block signatures after height %d", peer, this.ourHeight));
									return false;
								}
							}

							signature = signatures.get(0);
							signatures.remove(0);
							++this.ourHeight;

							Block newBlock = this.fetchBlock(repository, peer, signature);

							if (newBlock == null) {
								LOGGER.info(String.format("Peer %s failed to respond with block for height %d", peer, this.ourHeight));
								return false;
							}

							if (!newBlock.isSignatureValid()) {
								LOGGER.info(String.format("Peer %s sent block with invalid signature for height %d", peer, this.ourHeight));
								return false;
							}

							ValidationResult blockResult = newBlock.isValid();
							if (blockResult != ValidationResult.OK) {
								LOGGER.info(String.format("Peer %s sent invalid block for height %d: %s", peer, this.ourHeight, blockResult.name()));
								return false;
							}

							newBlock.process();
						}

						// Commit
						repository.saveChanges();
						LOGGER.info(String.format("Synchronized with peer %s to height %d", peer, this.ourHeight));

						return true;
					} finally {
						repository.discardChanges(); // Free repository locks, if any, also in case anything went wrong
						this.repository = null;
					}
				}
			} catch (DataException e) {
				LOGGER.error("Repository issue during synchronization with peer", e);
				return false;
			} finally {
				blockchainLock.unlock();
			}

		// Wasn't peer's fault we couldn't sync
		return true;
	}

	/**
	 * Returns list of block signatures start with common block with peer.
	 * 
	 * @param peer
	 * @return block signatures
	 * @throws DataException
	 */
	private List<byte[]> findSignaturesFromCommonBlock(Peer peer) throws DataException {
		// Start by asking for a few recent block hashes as this will cover a majority of reorgs
		// Failing that, back off exponentially
		int step = INITIAL_BLOCK_STEP;

		List<byte[]> blockSignatures = null;
		int testHeight = ourHeight - step;
		byte[] testSignature = null;

		while (testHeight > 1) {
			// Fetch our block signature at this height
			BlockData testBlockData = this.repository.getBlockRepository().fromHeight(testHeight);
			if (testBlockData == null) {
				// Not found? But we've locked the blockchain and height is below blockchain's tip!
				LOGGER.error("Failed to get block at height lower than blockchain tip during synchronization?");
				return null;
			}

			testSignature = testBlockData.getSignature();

			// Ask for block signatures since test block's signature
			LOGGER.trace(String.format("Requesting %d signature%s after our height %d", step, (step != 1 ? "s": ""), testHeight));
			blockSignatures = this.getBlockSignatures(peer, testSignature, step);

			if (blockSignatures == null)
				// No response - give up this time
				return null;

			LOGGER.trace(String.format("Received %s signature%s", blockSignatures.size(), (blockSignatures.size() != 1 ? "s" : "")));

			// Empty list means remote peer is unaware of test signature OR has no new blocks after test signature
			if (!blockSignatures.isEmpty())
				// We have entries so we have found a common block
				break;

			if (peer.getVersion() >= 2) {
				step <<= 1;
			} else {
				// Old v1 peers are hard-coded to return 500 signatures so we might as well go backward by 500 too
				step = 500;
			}
			step = Math.min(step, MAXIMUM_BLOCK_STEP);

			testHeight -= step;
		}

		if (testHeight <= 1)
			// Can't go back any further - return Genesis block
			return new ArrayList<byte[]>(Arrays.asList(GenesisBlock.getInstance(this.repository).getBlockData().getSignature()));

		// Prepend common block's signature as first block sig
		blockSignatures.add(0, testSignature);

		// Work through returned signatures to get closer common block
		// Do this by trimming all-but-one leading known signatures
		for (int i = blockSignatures.size() - 1; i > 0; --i) {
			BlockData blockData = this.repository.getBlockRepository().fromSignature(blockSignatures.get(i));

			if (blockData != null) {
				blockSignatures.subList(0, i).clear();
				break;
			}
		}

		return blockSignatures;
	}

	private List<byte[]> getBlockSignatures(Peer peer, byte[] parentSignature, int numberRequested) {
		// TODO numberRequested is v2+ feature
		Message getSignaturesMessage = new GetSignaturesMessage(parentSignature);

		Message message = peer.getResponse(getSignaturesMessage);
		if (message == null || message.getType() != MessageType.SIGNATURES)
			return null;

		SignaturesMessage signaturesMessage = (SignaturesMessage) message;

		return signaturesMessage.getSignatures();
	}

	private Block fetchBlock(Repository repository, Peer peer, byte[] signature) {
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
