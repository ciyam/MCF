package qora.block;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.account.PrivateKeyAccount;
import qora.block.Block.ValidationResult;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

// Forging new blocks

// How is the private key going to be supplied?

public class BlockGenerator extends Thread {

	// Properties
	private byte[] generatorPrivateKey;
	private PrivateKeyAccount generator;
	private Block previousBlock;
	private Block newBlock;
	private boolean running;

	// Other properties
	private static final Logger LOGGER = LogManager.getLogger(BlockGenerator.class);

	// Constructors

	public BlockGenerator(byte[] generatorPrivateKey) {
		this.generatorPrivateKey = generatorPrivateKey;
		this.previousBlock = null;
		this.newBlock = null;
		this.running = true;
	}

	// Main thread loop
	@Override
	public void run() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			generator = new PrivateKeyAccount(repository, generatorPrivateKey);

			// Going to need this a lot...
			BlockRepository blockRepository = repository.getBlockRepository();

			while (running) {
				// Check blockchain hasn't changed
				BlockData lastBlockData = blockRepository.getLastBlock();
				if (previousBlock == null || !Arrays.equals(previousBlock.getSignature(), lastBlockData.getSignature())) {
					previousBlock = new Block(repository, lastBlockData);
					newBlock = null;
				}

				// Do we need to build a potential new block?
				if (newBlock == null)
					newBlock = new Block(repository, previousBlock.getBlockData(), generator);

				// Is new block valid yet? (Before adding unconfirmed transactions)
				if (newBlock.isValid() == ValidationResult.OK) {
					// Add unconfirmed transactions
					addUnconfirmedTransactions(repository, newBlock);

					// Sign to create block's signature
					newBlock.sign();

					// If newBlock is still valid then we can use it
					ValidationResult validationResult = newBlock.isValid();
					if (validationResult == ValidationResult.OK) {
						// Add to blockchain - something else will notice and broadcast new block to network
						try {
							newBlock.process();
							LOGGER.info("Generated new block: " + newBlock.getBlockData().getHeight());
							repository.saveChanges();
						} catch (DataException e) {
							// Unable to process block - report and discard
							LOGGER.error("Unable to process newly generated block?", e);
							newBlock = null;
						}
					} else {
						// No longer valid? Report and discard
						LOGGER.error("Valid, generated block now invalid '" + validationResult.name() + "' after adding unconfirmed transactions?");
						newBlock = null;
					}
				}

				// Sleep for a while
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// We've been interrupted - time to exit
					return;
				}
			}
		} catch (DataException e) {
			LOGGER.warn("Repository issue while running block generator", e);
		}
	}

	private void addUnconfirmedTransactions(Repository repository, Block newBlock) throws DataException {
		// Grab all unconfirmed transactions (already sorted)
		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getAllUnconfirmedTransactions();

		// Attempt to add transactions until block is full, or we run out
		for (TransactionData transactionData : unconfirmedTransactions)
			if (!newBlock.addTransaction(transactionData))
				break;
	}

	public void shutdown() {
		this.running = false;
		// Interrupt too, absorbed by HSQLDB but could be caught by Thread.sleep()
		Thread.currentThread().interrupt();
	}

}
