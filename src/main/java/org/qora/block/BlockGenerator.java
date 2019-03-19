package org.qora.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.Block.ValidationResult;
import org.qora.controller.Controller;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
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

		List<byte[]> generatorKeys = Settings.getInstance().getGeneratorKeys();
		// No generators?
		if (generatorKeys.isEmpty())
			return;

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

			List<PrivateKeyAccount> generators = generatorKeys.stream().map(key -> new PrivateKeyAccount(repository, key)).collect(Collectors.toList());

			// Going to need this a lot...
			BlockRepository blockRepository = repository.getBlockRepository();
			Block previousBlock = null;

			List<Block> newBlocks = null;

			while (running) {
				// Check blockchain hasn't changed
				BlockData lastBlockData = blockRepository.getLastBlock();
				if (previousBlock == null || !Arrays.equals(previousBlock.getSignature(), lastBlockData.getSignature())) {
					previousBlock = new Block(repository, lastBlockData);
					newBlocks = null;
				}

				// Do we need to build a potential new blocks?
				if (newBlocks == null) {
					// First block does the AT heavy-lifting
					newBlocks = new ArrayList<>(generators.size());
					Block newBlock = new Block(repository, previousBlock.getBlockData(), generators.get(0));
					newBlocks.add(newBlock);

					// The blocks for other generators require less effort...
					for (int i = 1; i < generators.size(); ++i)
						newBlocks.add(newBlock.regenerate(generators.get(i)));
				}

				// Make sure we're the only thread modifying the blockchain
				Lock blockchainLock = Controller.getInstance().getBlockchainLock();
				if (blockchainLock.tryLock())
					generation: try {
						List<Block> goodBlocks = new ArrayList<>();

						for (Block testBlock : newBlocks) {
							// Is new block's timestamp valid yet?
							// We do a separate check as some timestamp checks are skipped for testnet
							if (testBlock.isTimestampValid() != ValidationResult.OK)
								continue;

							// Is new block valid yet? (Before adding unconfirmed transactions)
							if (testBlock.isValid() != ValidationResult.OK)
								continue;

							goodBlocks.add(testBlock);
						}

						if (goodBlocks.isEmpty())
							break generation;

						// Pick random generator
						int winningIndex = new Random().nextInt(goodBlocks.size());
						Block newBlock = goodBlocks.get(winningIndex);

						// Delete invalid transactions
						deleteInvalidTransactions(repository);

						// Add unconfirmed transactions
						addUnconfirmedTransactions(repository, newBlock);

						// Sign to create block's signature
						newBlock.sign();

						// Is newBlock still valid?
						ValidationResult validationResult = newBlock.isValid();
						if (validationResult != ValidationResult.OK) {
							// No longer valid? Report and discard
							LOGGER.error("Valid, generated block now invalid '" + validationResult.name() + "' after adding unconfirmed transactions?");
							newBlock = null;
							break generation;
						}

						// Add to blockchain - something else will notice and broadcast new block to network
						try {
							newBlock.process();
							LOGGER.info("Generated new block: " + newBlock.getBlockData().getHeight());
							repository.saveChanges();

							// Notify controller
							Controller.getInstance().onGeneratedBlock(newBlock.getBlockData());
						} catch (DataException e) {
							// Unable to process block - report and discard
							LOGGER.error("Unable to process newly generated block?", e);
							newBlock = null;
						}
					} finally {
						blockchainLock.unlock();
					}

				// Sleep for a while
				try {
					repository.discardChanges(); // Free repository locks, if any
					Thread.sleep(1000); // No point sleeping less than this as block timestamp millisecond values must be the same
				} catch (InterruptedException e) {
					// We've been interrupted - time to exit
					return;
				}
			}
		} catch (DataException e) {
			LOGGER.warn("Repository issue while running block generator", e);
		}
	}

	private static void deleteInvalidTransactions(Repository repository) throws DataException {
		List<TransactionData> invalidTransactions = Transaction.getInvalidTransactions(repository);

		// Actually delete invalid transactions from database
		for (TransactionData invalidTransactionData : invalidTransactions) {
			LOGGER.trace(String.format("Deleting invalid, unconfirmed transaction %s", Base58.encode(invalidTransactionData.getSignature())));
			repository.getTransactionRepository().delete(invalidTransactionData);
		}
		repository.saveChanges();
	}

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

			// Ignore transactions that have not met group-admin approval threshold
			if (transaction.needsGroupApproval() && !transaction.meetsGroupApprovalThreshold()) {
				unconfirmedTransactions.remove(i);
				--i;
				continue;
			}
		}

		// Attempt to add transactions until block is full, or we run out
		// If a transaction makes the block invalid then skip it and it'll either expire or be in next block.
		for (TransactionData transactionData : unconfirmedTransactions) {
			if (!newBlock.addTransaction(transactionData))
				break;

			// Sign to create block's signature
			newBlock.sign();

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
		if (!BlockChain.getInstance().isTestNet()) {
			LOGGER.warn("Attempt to generating testing block but not in testnet mode!");
			return;
		}

		BlockData previousBlockData = repository.getBlockRepository().getLastBlock();

		Block newBlock = new Block(repository, previousBlockData, generator);

		// Make sure we're the only thread modifying the blockchain
		Lock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (blockchainLock.tryLock())
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
