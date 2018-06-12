package qora.block;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.primitives.Bytes;

import data.block.BlockData;
import data.block.BlockTransactionData;
import data.transaction.TransactionData;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import transform.TransformationException;
import transform.block.BlockTransformer;
import transform.transaction.TransactionTransformer;
import utils.NTP;

/*
 * Typical use-case scenarios:
 * 
 * 1. Loading a Block from the database using height, signature, reference, etc.
 * 2. Generating a new block, adding unconfirmed transactions
 * 3. Receiving a block from another node
 * 
 * Transaction count, transactions signature and total fees need to be maintained by Block.
 * In scenario (1) these can be found in database.
 * In scenarios (2) and (3) Transactions are added to the Block via addTransaction() method.
 * Also in scenarios (2) and (3), Block is responsible for saving Transactions to DB.
 * 
 * When is height set?
 * In scenario (1) this can be found in database.
 * In scenarios (2) and (3) this will need to be set after successful processing,
 * but before Block is saved into database.
 * 
 * GeneratorSignature's data is: reference + generatingBalance + generator's public key
 * TransactionSignature's data is: generatorSignature + transaction signatures
 * Block signature is: generatorSignature + transactionsSignature
 */

public class Block {

	// Properties
	private Repository repository;
	private BlockData blockData;
	private PublicKeyAccount generator;
	
	// Other properties
	protected List<Transaction> transactions;
	protected BigDecimal cachedNextGeneratingBalance;

	// Other useful constants
	public static final int MAX_BLOCK_BYTES = 1048576;
	/**
	 * Number of blocks between recalculating block's generating balance.
	 */
	private static final int BLOCK_RETARGET_INTERVAL = 10;
	/**
	 * Maximum acceptable timestamp disagreement offset in milliseconds.
	 */
	private static final long BLOCK_TIMESTAMP_MARGIN = 500L;

	// Various release timestamps / block heights
	public static final int MESSAGE_RELEASE_HEIGHT = 99000;
	public static final int AT_BLOCK_HEIGHT_RELEASE = 99000;
	public static final long POWFIX_RELEASE_TIMESTAMP = 1456426800000L; // Block Version 3 // 2016-02-25T19:00:00+00:00
	public static final long ASSETS_RELEASE_TIMESTAMP = 0L; // From Qora epoch

	// Constructors

	public Block(Repository repository, BlockData blockData) {
		this.repository = repository;
		this.blockData = blockData;
		this.generator = new PublicKeyAccount(blockData.getGeneratorPublicKey());
	}

	// Getters/setters

	public BlockData getBlockData() {
		return this.blockData;
	}

	// More information

	/**
	 * Return composite block signature (generatorSignature + transactionsSignature).
	 * 
	 * @return byte[], or null if either component signature is null.
	 */
	public byte[] getSignature() {
		if (this.blockData.getGeneratorSignature() == null || this.blockData.getTransactionsSignature() == null)
			return null;

		return Bytes.concat(this.blockData.getGeneratorSignature(), this.blockData.getTransactionsSignature());
	}

	/**
	 * Return the next block's version.
	 * 
	 * @return 1, 2 or 3
	 */
	public int getNextBlockVersion() {
		if (this.blockData.getHeight() < AT_BLOCK_HEIGHT_RELEASE)
			return 1;
		else if (this.blockData.getTimestamp() < POWFIX_RELEASE_TIMESTAMP)
			return 2;
		else
			return 3;
	}

	/**
	 * Return the next block's generating balance.
	 * <p>
	 * Every BLOCK_RETARGET_INTERVAL the generating balance is recalculated.
	 * <p>
	 * If this block starts a new interval then the new generating balance is calculated, cached and returned.<br>
	 * Within this interval, the generating balance stays the same so the current block's generating balance will be returned.
	 * 
	 * @return next block's generating balance
	 * @throws SQLException
	 */
	public BigDecimal getNextBlockGeneratingBalance() throws SQLException {
		// This block not at the start of an interval?
		if (this.blockData.getHeight() % BLOCK_RETARGET_INTERVAL != 0)
			return this.blockData.getGeneratingBalance();

		// Return cached calculation if we have one
		if (this.cachedNextGeneratingBalance != null)
			return this.cachedNextGeneratingBalance;

		// Perform calculation

		// Navigate back to first block in previous interval:
		// XXX: why can't we simply load using block height?
		BlockRepository blockRepo = this.repository.getBlockRepository();
		BlockData firstBlock = this.blockData;
		
		try {
			for (int i = 1; firstBlock != null && i < BLOCK_RETARGET_INTERVAL; ++i)
				firstBlock = blockRepo.fromSignature(firstBlock.getReference());
		} catch (DataException e) {
			firstBlock = null;
		}

		// Couldn't navigate back far enough?
		if (firstBlock == null)
			throw new IllegalStateException("Failed to calculate next block's generating balance due to lack of historic blocks");

		// Calculate the actual time period (in ms) over previous interval's blocks.
		long previousGeneratingTime = this.blockData.getTimestamp() - firstBlock.getTimestamp();

		// Calculate expected forging time (in ms) for a whole interval based on this block's generating balance.
		long expectedGeneratingTime = Block.calcForgingDelay(this.blockData.getGeneratingBalance()) * BLOCK_RETARGET_INTERVAL * 1000;

		// Finally, scale generating balance such that faster than expected previous intervals produce larger generating balances.
		BigDecimal multiplier = BigDecimal.valueOf((double) expectedGeneratingTime / (double) previousGeneratingTime);
		this.cachedNextGeneratingBalance = BlockChain.minMaxBalance(this.blockData.getGeneratingBalance().multiply(multiplier));

		return this.cachedNextGeneratingBalance;
	}

	/**
	 * Return expected forging delay, in seconds, since previous block based on block's generating balance.
	 */
	public static long calcForgingDelay(BigDecimal generatingBalance) {
		generatingBalance = BlockChain.minMaxBalance(generatingBalance);

		double percentageOfTotal = generatingBalance.divide(BlockChain.MAX_BALANCE).doubleValue();
		long actualBlockTime = (long) (BlockChain.MIN_BLOCK_TIME + ((BlockChain.MAX_BLOCK_TIME - BlockChain.MIN_BLOCK_TIME) * (1 - percentageOfTotal)));

		return actualBlockTime;
	}

	/**
	 * Return block's transactions.
	 * <p>
	 * If the block was loaded from repository then it's possible this method will call the repository to load the transactions if they are not already loaded.
	 * 
	 * @return
	 * @throws DataException
	 */
	public List<Transaction> getTransactions() throws DataException {
		// Already loaded?
		if (this.transactions != null)
			return this.transactions;

		// Allocate cache for results
		List<TransactionData> transactionsData = this.repository.getBlockRepository().getTransactionsFromSignature(this.blockData.getSignature());

		// The number of transactions fetched from repository should correspond with Block's transactionCount
		if (transactionsData.size() != this.blockData.getTransactionCount())
			throw new IllegalStateException("Block's transactions from repository do not match block's transaction count");

		this.transactions = new ArrayList<Transaction>();
		
		for (TransactionData transactionData : transactionsData)
			this.transactions.add(Transaction.fromData(transactionData));
		
		return this.transactions;
	}

	// Processing

	/**
	 * Add a transaction to the block.
	 * <p>
	 * Used when constructing a new block during forging.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount} so block's transactions signature can be recalculated.
	 * 
	 * @param transactionData
	 * @return true if transaction successfully added to block, false otherwise
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 */
	public boolean addTransaction(TransactionData transactionData) {
		// Can't add to transactions if we haven't loaded existing ones yet
		if (this.transactions == null)
			throw new IllegalStateException("Attempted to add transaction to partially loaded database Block");

		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		// Check there is space in block
		try {
			if (BlockTransformer.getDataLength(this) + TransactionTransformer.getDataLength(transactionData) > MAX_BLOCK_BYTES)
				return false;
		} catch (TransformationException e) {
			return false;
		}

		// Add to block
		this.transactions.add(Transaction.fromData(transactionData));

		// Update transaction count
		this.blockData.setTransactionCount(this.blockData.getTransactionCount() + 1);

		// Update totalFees
		this.blockData.setTotalFees(this.blockData.getTotalFees().add(transactionData.getFee()));

		// Update transactions signature
		calcTransactionsSignature();

		return true;
	}

	/**
	 * Recalculate block's generator signature.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount}.
	 * 
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 * @throws RuntimeException
	 *             if somehow the generator signature cannot be calculated
	 */
	public void calcGeneratorSignature() {
		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		try {
			this.blockData.setGeneratorSignature(((PrivateKeyAccount) this.generator).sign(BlockTransformer.getBytesForGeneratorSignature(this.blockData)));
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to calculate block's generator signature", e);
		}
	}

	/**
	 * Recalculate block's transactions signature.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount}.
	 * 
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 * @throws RuntimeException
	 *             if somehow the transactions signature cannot be calculated
	 */
	public void calcTransactionsSignature() {
		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		try {
			this.blockData.setTransactionsSignature(((PrivateKeyAccount) this.generator).sign(BlockTransformer.getBytesForTransactionsSignature(this)));
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to calculate block's transactions signature", e);
		}
	}


	public boolean isSignatureValid() {
		try {
			// Check generator's signature first
			if (!this.generator.verify(this.blockData.getGeneratorSignature(), BlockTransformer.getBytesForGeneratorSignature(this.blockData)))
				return false;

			// Check transactions signature
			if (!this.generator.verify(this.blockData.getTransactionsSignature(), BlockTransformer.getBytesForTransactionsSignature(this)))
				return false;
		} catch (TransformationException e) {
			return false;
		}

		return true;
	}

	/**
	 * Returns whether Block is valid.
	 * <p>
	 * Performs various tests like checking for parent block, correct block timestamp, version, generating balance, etc.
	 * <p>
	 * Checks block's transactions using an HSQLDB "SAVEPOINT" and hence needs to be called within an ongoing SQL Transaction.
	 * 
	 * @return true if block is valid, false otherwise.
	 * @throws SQLException
	 * @throws DataException 
	 */
	public boolean isValid() throws SQLException, DataException {
		// TODO

		// Check parent blocks exists
		if (this.blockData.getReference() == null)
			return false;

		BlockData parentBlockData = this.repository.getBlockRepository().fromSignature(this.blockData.getReference());
		if (parentBlockData == null)
			return false;

		Block parentBlock = new Block(this.repository, parentBlockData);
		
		// Check timestamp is valid, i.e. later than parent timestamp and not in the future, within ~500ms margin
		if (this.blockData.getTimestamp() < parentBlockData.getTimestamp() || this.blockData.getTimestamp() - BLOCK_TIMESTAMP_MARGIN > NTP.getTime())
			return false;

		// Legacy gen1 test: check timestamp ms is the same as parent timestamp ms?
		if (this.blockData.getTimestamp() % 1000 != parentBlockData.getTimestamp() % 1000)
			return false;

		// Check block version
		if (this.blockData.getVersion() != parentBlock.getNextBlockVersion())
			return false;
		if (this.blockData.getVersion() < 2 && (this.blockData.getAtBytes() != null || this.blockData.getAtFees() != null))
			return false;

		// Check generating balance
		if (this.blockData.getGeneratingBalance() != parentBlock.getNextBlockGeneratingBalance())
			return false;

		// Check generator's proof of stake against block's generating balance
		// TODO

		// Check CIYAM AT
		if (this.blockData.getAtBytes() != null && this.blockData.getAtBytes().length > 0) {
			// TODO
			// try {
			// AT_Block atBlock = AT_Controller.validateATs(this.getBlockATs(), BlockChain.getHeight() + 1);
			// this.atFees = atBlock.getTotalFees();
			// } catch (NoSuchAlgorithmException | AT_Exception e) {
			// return false;
			// }
		}

		// Check transactions
		try {
			for (Transaction transaction : this.getTransactions()) {
				// GenesisTransactions are not allowed (GenesisBlock overrides isValid() to allow them)
				if (transaction instanceof GenesisTransaction)
					return false;

				// Check timestamp and deadline
				if (transaction.getTransactionData().getTimestamp() > this.blockData.getTimestamp()
						|| transaction.getDeadline() <= this.blockData.getTimestamp())
					return false;

				// Check transaction is even valid
				// NOTE: in Gen1 there was an extra block height passed to DeployATTransaction.isValid
				if (transaction.isValid() != Transaction.ValidationResult.OK)
					return false;

				// Process transaction to make sure other transactions validate properly
				try {
					transaction.process();
				} catch (Exception e) {
					// LOGGER.error("Exception during transaction processing, tx " + Base58.encode(transaction.getSignature()), e);
					return false;
				}
			}
		} catch (DataException e) {
			return false;
		} finally {
			// Revert back to savepoint
			try {
				this.repository.discardChanges();
			} catch (DataException e) {
				/*
				 * Rollback failure most likely due to prior DataException, so catch rollback's DataException and discard. A "return false" in try-block will
				 * still return false, prior DataException propagates to caller and successful completion of try-block continues on after rollback.
				 */
			}
		}

		// Block is valid
		return true;
	}

	public void process() throws DataException, SQLException {
		// Process transactions (we'll link them to this block after saving the block itself)
		List<Transaction> transactions = this.getTransactions();
		for (Transaction transaction : transactions)
			transaction.process();

		// If fees are non-zero then add fees to generator's balance
		BigDecimal blockFee = this.blockData.getTotalFees();
		if (blockFee.compareTo(BigDecimal.ZERO) == 1)
			this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).add(blockFee));

		// Link block into blockchain by fetching signature of highest block and setting that as our reference
		int blockchainHeight = BlockChain.getHeight();
		BlockData latestBlockData = this.repository.getBlockRepository().fromHeight(blockchainHeight);
		if (latestBlockData != null)
			this.blockData.setReference(latestBlockData.getSignature());

		this.blockData.setHeight(blockchainHeight + 1);
		this.repository.getBlockRepository().save(this.blockData);

		// Link transactions to this block, thus removing them from unconfirmed transactions list.
		for (int sequence = 0; sequence < transactions.size(); ++sequence) {
			Transaction transaction = transactions.get(sequence);

			// Link transaction to this block
			BlockTransactionData blockTransactionData = new BlockTransactionData(this.getSignature(), sequence, transaction.getTransactionData().getSignature());
			this.repository.getBlockRepository().save(blockTransactionData);
		}
	}

	public void orphan(Connection connection) {
		// TODO
	}

}
