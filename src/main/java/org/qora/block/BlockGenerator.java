package org.qora.block;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.PrivateKeyAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.block.Block.ValidationResult;
import org.qora.block.BlockChain.BlockTimingByHeight;
import org.qora.controller.Controller;
import org.qora.data.account.ForgingAccountData;
import org.qora.data.account.ProxyForgerData;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockSummaryData;
import org.qora.data.transaction.TransactionData;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.transaction.Transaction;
import org.qora.utils.Base58;

// Forging new blocks

// How is the private key going to be supplied?

public class BlockGenerator extends Thread {

	// Properties
	private boolean running;

	// Other properties
	private static final Logger LOGGER = LogManager.getLogger(BlockGenerator.class);

	// Constructors

	public BlockGenerator() {
		this.running = true;
	}

	// Main thread loop
	@Override
	public void run() {
		Thread.currentThread().setName("BlockGenerator");

		try (final Repository repository = RepositoryManager.getRepository()) {
			if (Settings.getInstance().getWipeUnconfirmedOnStart()) {
				// Wipe existing unconfirmed transactions
				List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getUnconfirmedTransactions();

				for (TransactionData transactionData : unconfirmedTransactions) {
					LOGGER.trace(String.format("Deleting unconfirmed transaction %s", Base58.encode(transactionData.getSignature())));
					repository.getTransactionRepository().delete(transactionData);
				}

				repository.saveChanges();
			}

			// Going to need this a lot...
			BlockRepository blockRepository = repository.getBlockRepository();
			Block previousBlock = null;

			List<Block> newBlocks = new ArrayList<>();

			while (running) {
				// Sleep for a while
				try {
					repository.discardChanges(); // Free repository locks, if any
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// We've been interrupted - time to exit
					return;
				}

				List<Peer> peers = Network.getInstance().getUniqueHandshakedPeers();
				BlockData lastBlockData = blockRepository.getLastBlock();

				// Disregard peers that have "misbehaved" recently
				peers.removeIf(Controller.hasPeerMisbehaved);

				// Don't generate if we don't have enough connected peers as where would the transactions/consensus come from?
				if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
					continue;

				final long minLatestBlockTimestamp = Controller.getMinimumLatestBlockTimestamp();

				// Disregard peers that don't have a recent block
				peers.removeIf(peer -> peer.getLastBlockTimestamp() == null || peer.getLastBlockTimestamp() < minLatestBlockTimestamp);

				// If we have any peers with a recent block, but our latest block isn't recent
				// then we need to synchronize instead of generating.
				if (!peers.isEmpty() && lastBlockData.getTimestamp() < minLatestBlockTimestamp)
					continue;

				// There are no peers with a recent block and/or our latest block is recent
				// so go ahead and generate a block if possible.

				// Check blockchain hasn't changed
				if (previousBlock == null || !Arrays.equals(previousBlock.getSignature(), lastBlockData.getSignature())) {
					previousBlock = new Block(repository, lastBlockData);
					newBlocks.clear();
				}

				// Too early to generate any new blocks?
				BlockTimingByHeight blockTiming = BlockChain.getInstance().getBlockTimingByHeight(lastBlockData.getHeight() + 1);

				boolean tooEarlyToForge = System.currentTimeMillis() < lastBlockData.getTimestamp() + blockTiming.target - blockTiming.deviation;
				if (newBlocks.isEmpty() && tooEarlyToForge)
					continue;

				// Do we need to build any potential new blocks?
				List<ForgingAccountData> forgingAccountsData = repository.getAccountRepository().getForgingAccounts();
				// No forging accounts?
				if (forgingAccountsData.isEmpty())
					continue;

				List<PrivateKeyAccount> forgingAccounts = forgingAccountsData.stream().map(accountData -> new PrivateKeyAccount(repository, accountData.getSeed())).collect(Collectors.toList());

				// Discard accounts we have blocks for
				forgingAccounts.removeIf(account -> newBlocks.stream().anyMatch(newBlock -> newBlock.getGenerator().getAddress().equals(account.getAddress())));

				for (PrivateKeyAccount generator : forgingAccounts) {
					// Too early for this generator to generate a new block?
					if (System.currentTimeMillis() < Block.calcMinimumTimestamp(previousBlock.getBlockData(), generator.getPublicKey()))
						continue;

					// First block does the AT heavy-lifting
					if (newBlocks.isEmpty()) {
						Block newBlock = new Block(repository, previousBlock.getBlockData(), generator, System.currentTimeMillis());
						newBlocks.add(newBlock);
					} else {
						// The blocks for other generators require less effort...
						Block newBlock = newBlocks.get(0);
						newBlocks.add(newBlock.regenerate(generator));
					}
				}

				// No potential block candidates?
				if (newBlocks.isEmpty())
					continue;

				// Make sure we're the only thread modifying the blockchain
				ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
				if (!blockchainLock.tryLock())
					continue;

				boolean newBlockGenerated = false;

				try {
					// Clear repository's "in transaction" state so we don't cause a repository deadlock
					repository.discardChanges();

					// Pick best block

					// Convert block candidates into a list of single-entry blockchain summaries
					List<List<BlockSummaryData>> blockSummaries = newBlocks.stream().map(block -> Arrays.asList(new BlockSummaryData(block.getBlockData()))).collect(Collectors.toList());

					BlockSummaryData parentBlockSummary = new BlockSummaryData(previousBlock.getBlockData());
					List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, blockSummaries);

					int indexOfBest = 0;
					for (int i = 1; i < distances.size(); ++i)
						if (distances.get(i).compareTo(distances.get(indexOfBest)) < 0)
							indexOfBest = i;

					Block bestBlock = newBlocks.get(indexOfBest);

					// Is new block's timestamp valid yet?
					// We do a separate check as some timestamp checks are skipped for testnet
					if (bestBlock.isTimestampValid() != ValidationResult.OK)
						continue;

					// Is new block valid yet? (Before adding unconfirmed transactions)
					if (bestBlock.isValid() != ValidationResult.OK)
						continue;

					// Delete invalid transactions. NOTE: discards repository changes on entry, saves changes on exit.
					deleteInvalidTransactions(repository);

					// Add unconfirmed transactions
					addUnconfirmedTransactions(repository, bestBlock);

					// Sign to create block's signature
					bestBlock.sign();

					// Is newBlock still valid?
					ValidationResult validationResult = bestBlock.isValid();
					if (validationResult != ValidationResult.OK) {
						// No longer valid? Report and discard
						LOGGER.error("Valid, generated block now invalid '" + validationResult.name() + "' after adding unconfirmed transactions?");

						// Rebuild block candidates, just to be sure
						newBlocks.clear();
						continue;
					}

					// Add to blockchain - something else will notice and broadcast new block to network
					try {
						bestBlock.process();

						ProxyForgerData proxyForgerData = repository.getAccountRepository().getProxyForgeData(bestBlock.getBlockData().getGeneratorPublicKey());

						if (proxyForgerData != null) {
							PublicKeyAccount forger = new PublicKeyAccount(repository, proxyForgerData.getForgerPublicKey());
							LOGGER.info(String.format("Generated block %d by %s on behalf of %s",
									bestBlock.getBlockData().getHeight(),
									forger.getAddress(),
									proxyForgerData.getRecipient()));
						} else {
							LOGGER.info(String.format("Generated block %d by %s", bestBlock.getBlockData().getHeight(), bestBlock.getGenerator().getAddress()));
						}

						repository.saveChanges();

						// Notify controller
						newBlockGenerated = true;
					} catch (DataException e) {
						// Unable to process block - report and discard
						LOGGER.error("Unable to process newly generated block?", e);
						newBlocks.clear();
					}
				} finally {
					blockchainLock.unlock();
				}

				if (newBlockGenerated)
					Controller.getInstance().onGeneratedBlock();
			}
		} catch (DataException e) {
			LOGGER.warn("Repository issue while running block generator", e);
		}
	}

	/**
	 * Deletes invalid, unconfirmed transactions from repository.
	 * <p>
	 * NOTE: calls Transaction.getInvalidTransactions which discards uncommitted
	 * repository changes.
	 * <p>
	 * Also commits the deletion of invalid transactions to the repository.
	 *  
	 * @param repository
	 * @throws DataException
	 */
	private static void deleteInvalidTransactions(Repository repository) throws DataException {
		List<TransactionData> invalidTransactions = Transaction.getInvalidTransactions(repository);

		// Actually delete invalid transactions from database
		for (TransactionData invalidTransactionData : invalidTransactions) {
			LOGGER.trace(String.format("Deleting invalid, unconfirmed transaction %s", Base58.encode(invalidTransactionData.getSignature())));
			repository.getTransactionRepository().delete(invalidTransactionData);
		}

		repository.saveChanges();
	}

	/**
	 * Adds unconfirmed transactions to passed block.
	 * <p>
	 * NOTE: calls Transaction.getUnconfirmedTransactions which discards uncommitted
	 * repository changes.
	 * 
	 * @param repository
	 * @param newBlock
	 * @throws DataException
	 */
	private static void addUnconfirmedTransactions(Repository repository, Block newBlock) throws DataException {
		// Grab all valid unconfirmed transactions (already sorted)
		List<TransactionData> unconfirmedTransactions = Transaction.getUnconfirmedTransactions(repository);

		for (int i = 0; i < unconfirmedTransactions.size(); ++i) {
			TransactionData transactionData = unconfirmedTransactions.get(i);

			// Ignore transactions that have timestamp later than block's timestamp (not yet valid)
			if (transactionData.getTimestamp() > newBlock.getBlockData().getTimestamp()) {
				unconfirmedTransactions.remove(i);
				--i;
				continue;
			}

			Transaction transaction = Transaction.fromData(repository, transactionData);

			// Ignore transactions that have expired before this block - they will be cleaned up later
			if (transaction.getDeadline() <= newBlock.getBlockData().getTimestamp()) {
				unconfirmedTransactions.remove(i);
				--i;
				continue;
			}
		}

		// Sign to create block's signature, needed by Block.isValid()
		newBlock.sign();

		// Attempt to add transactions until block is full, or we run out
		// If a transaction makes the block invalid then skip it and it'll either expire or be in next block.
		for (TransactionData transactionData : unconfirmedTransactions) {
			if (!newBlock.addTransaction(transactionData))
				break;

			// If newBlock is no longer valid then we can't use transaction
			ValidationResult validationResult = newBlock.isValid();
			if (validationResult != ValidationResult.OK) {
				LOGGER.debug("Skipping invalid transaction " + Base58.encode(transactionData.getSignature()) + " during block generation");
				newBlock.deleteTransaction(transactionData);
			}
		}
	}

	public void shutdown() {
		this.running = false;
		// Interrupt too, absorbed by HSQLDB but could be caught by Thread.sleep()
		this.interrupt();
	}

	public static void generateTestingBlock(Repository repository, PrivateKeyAccount generator) throws DataException {
		if (!BlockChain.getInstance().isTestChain()) {
			LOGGER.warn("Ignoring attempt to generate testing block for non-test chain!");
			return;
		}

		BlockData previousBlockData = repository.getBlockRepository().getLastBlock();

		Block newBlock = new Block(repository, previousBlockData, generator, System.currentTimeMillis());

		// Make sure we're the only thread modifying the blockchain
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		blockchainLock.lock();
		try {
			// Delete invalid transactions
			deleteInvalidTransactions(repository);

			// Add unconfirmed transactions
			addUnconfirmedTransactions(repository, newBlock);

			// Sign to create block's signature
			newBlock.sign();

			// Is newBlock still valid?
			ValidationResult validationResult = newBlock.isValid();
			if (validationResult != ValidationResult.OK)
				throw new IllegalStateException(
						"Valid, generated block now invalid '" + validationResult.name() + "' after adding unconfirmed transactions?");

			// Add to blockchain
			newBlock.process();
			repository.saveChanges();
		} finally {
			blockchainLock.unlock();
		}
	}

}
