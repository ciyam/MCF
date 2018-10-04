package qora.block;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import data.block.BlockData;
import data.block.BlockTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import transform.TransformationException;
import transform.block.BlockTransformer;
import transform.transaction.TransactionTransformer;
import utils.Base58;
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

	// Validation results
	public enum ValidationResult {
		OK(1),
		REFERENCE_MISSING(10),
		PARENT_DOES_NOT_EXIST(11),
		BLOCKCHAIN_NOT_EMPTY(12),
		TIMESTAMP_OLDER_THAN_PARENT(20),
		TIMESTAMP_IN_FUTURE(21),
		TIMESTAMP_MS_INCORRECT(22),
		VERSION_INCORRECT(30),
		FEATURE_NOT_YET_RELEASED(31),
		GENERATING_BALANCE_INCORRECT(40),
		GENERATOR_NOT_ACCEPTED(41),
		GENESIS_TRANSACTIONS_INVALID(50),
		TRANSACTION_TIMESTAMP_INVALID(51),
		TRANSACTION_INVALID(52),
		TRANSACTION_PROCESSING_FAILED(53);

		public final int value;

		private final static Map<Integer, ValidationResult> map = stream(ValidationResult.values()).collect(toMap(result -> result.value, result -> result));

		ValidationResult(int value) {
			this.value = value;
		}

		public static ValidationResult valueOf(int value) {
			return map.get(value);
		}
	}

	// Properties
	protected Repository repository;
	protected BlockData blockData;
	protected PublicKeyAccount generator;

	// Other properties
	private static final Logger LOGGER = LogManager.getLogger(Block.class);
	protected List<Transaction> transactions;
	protected BigDecimal cachedNextGeneratingBalance;

	// Other useful constants
	public static final int MAX_BLOCK_BYTES = 1048576;

	// Constructors

	public Block(Repository repository, BlockData blockData) throws DataException {
		this.repository = repository;
		this.blockData = blockData;
		this.generator = new PublicKeyAccount(repository, blockData.getGeneratorPublicKey());
	}

	// When receiving a block over network?
	public Block(Repository repository, BlockData blockData, List<TransactionData> transactions) throws DataException {
		this(repository, blockData);

		this.transactions = new ArrayList<Transaction>();

		// We have to sum fees too
		for (TransactionData transactionData : transactions) {
			this.transactions.add(Transaction.fromData(repository, transactionData));
			this.blockData.setTotalFees(this.blockData.getTotalFees().add(transactionData.getFee()));
		}
	}

	// For creating a new block?
	public Block(Repository repository, int version, byte[] reference, long timestamp, BigDecimal generatingBalance, PrivateKeyAccount generator,
			byte[] atBytes, BigDecimal atFees) {
		this.repository = repository;
		this.generator = generator;

		this.blockData = new BlockData(version, reference, 0, BigDecimal.ZERO.setScale(8), null, 0, timestamp, generatingBalance, generator.getPublicKey(),
				null, atBytes, atFees);

		this.transactions = new ArrayList<Transaction>();
	}

	/** Construct a new block for use in tests */
	public Block(Repository repository, BlockData parentBlockData, PrivateKeyAccount generator, byte[] atBytes, BigDecimal atFees) throws DataException {
		this.repository = repository;
		this.generator = generator;

		Block parentBlock = new Block(repository, parentBlockData);

		int version = parentBlock.getNextBlockVersion();
		byte[] reference = parentBlockData.getSignature();
		BigDecimal generatingBalance = parentBlock.calcNextBlockGeneratingBalance();

		byte[] generatorSignature;
		try {
			generatorSignature = generator
					.sign(BlockTransformer.getBytesForGeneratorSignature(parentBlockData.getGeneratorSignature(), generatingBalance, generator));
		} catch (TransformationException e) {
			throw new DataException("Unable to calculate next block generator signature", e);
		}

		long timestamp = parentBlock.calcNextBlockTimestamp(version, generatorSignature, generator);

		this.blockData = new BlockData(version, reference, 0, BigDecimal.ZERO.setScale(8), null, 0, timestamp, generatingBalance, generator.getPublicKey(),
				generatorSignature, atBytes, atFees);

		this.transactions = new ArrayList<Transaction>();
	}

	// Getters/setters

	public BlockData getBlockData() {
		return this.blockData;
	}

	public PublicKeyAccount getGenerator() {
		return this.generator;
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
		if (this.blockData.getHeight() < BlockChain.getATReleaseHeight())
			return 1;
		else if (this.blockData.getTimestamp() < BlockChain.getPowFixReleaseTimestamp())
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
	 * @throws DataException
	 */
	public BigDecimal calcNextBlockGeneratingBalance() throws DataException {
		if (this.blockData.getHeight() == 0)
			throw new IllegalStateException("Block height is unset");

		// This block not at the start of an interval?
		if (this.blockData.getHeight() % BlockChain.BLOCK_RETARGET_INTERVAL != 0)
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
			for (int i = 1; firstBlock != null && i < BlockChain.BLOCK_RETARGET_INTERVAL; ++i)
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
		long expectedGeneratingTime = Block.calcForgingDelay(this.blockData.getGeneratingBalance()) * BlockChain.BLOCK_RETARGET_INTERVAL * 1000;

		// Finally, scale generating balance such that faster than expected previous intervals produce larger generating balances.
		// NOTE: we have to use doubles and longs here to keep compatibility with Qora v1 results
		double multiplier = (double) expectedGeneratingTime / (double) previousGeneratingTime;
		long nextGeneratingBalance = (long) (this.blockData.getGeneratingBalance().doubleValue() * multiplier);

		this.cachedNextGeneratingBalance = BlockChain.minMaxBalance(BigDecimal.valueOf(nextGeneratingBalance).setScale(8));

		return this.cachedNextGeneratingBalance;
	}

	public static long calcBaseTarget(BigDecimal generatingBalance) {
		generatingBalance = BlockChain.minMaxBalance(generatingBalance);
		return generatingBalance.longValue() * calcForgingDelay(generatingBalance);
	}

	/**
	 * Return expected forging delay, in seconds, since previous block based on passed generating balance.
	 */
	public static long calcForgingDelay(BigDecimal generatingBalance) {
		generatingBalance = BlockChain.minMaxBalance(generatingBalance);

		double percentageOfTotal = generatingBalance.divide(BlockChain.MAX_BALANCE).doubleValue();
		long actualBlockTime = (long) (BlockChain.MIN_BLOCK_TIME + ((BlockChain.MAX_BLOCK_TIME - BlockChain.MIN_BLOCK_TIME) * (1 - percentageOfTotal)));

		return actualBlockTime;
	}

	private BigInteger calcGeneratorsTarget(Account nextBlockGenerator) throws DataException {
		// Start with 32-byte maximum integer representing all possible correct "guesses"
		// Where a "correct guess" is an integer greater than the threshold represented by calcBlockHash()
		byte[] targetBytes = new byte[32];
		Arrays.fill(targetBytes, Byte.MAX_VALUE);
		BigInteger target = new BigInteger(1, targetBytes);

		// Divide by next block's base target
		// So if next block requires a higher generating balance then there are fewer remaining "correct guesses"
		BigInteger baseTarget = BigInteger.valueOf(calcBaseTarget(calcNextBlockGeneratingBalance()));
		target = target.divide(baseTarget);

		// Multiply by account's generating balance
		// So the greater the account's generating balance then the greater the remaining "correct guesses"
		target = target.multiply(nextBlockGenerator.getGeneratingBalance().toBigInteger());

		return target;
	}

	private BigInteger calcBlockHash() {
		byte[] hashData;

		if (this.blockData.getVersion() < 3)
			hashData = this.blockData.getGeneratorSignature();
		else
			hashData = Bytes.concat(this.blockData.getReference(), generator.getPublicKey());

		// Calculate 32-byte hash as pseudo-random, but deterministic, integer (unique to this generator for v3+ blocks)
		byte[] hash = Crypto.digest(hashData);

		// Convert hash to BigInteger form
		return new BigInteger(1, hash);
	}

	private BigInteger calcNextBlockHash(int nextBlockVersion, byte[] preVersion3GeneratorSignature, PublicKeyAccount nextBlockGenerator) {
		byte[] hashData;

		if (nextBlockVersion < 3)
			hashData = preVersion3GeneratorSignature;
		else
			hashData = Bytes.concat(this.blockData.getSignature(), nextBlockGenerator.getPublicKey());

		// Calculate 32-byte hash as pseudo-random, but deterministic, integer (unique to this generator for v3+ blocks)
		byte[] hash = Crypto.digest(hashData);

		// Convert hash to BigInteger form
		return new BigInteger(1, hash);
	}

	private long calcNextBlockTimestamp(int nextBlockVersion, byte[] nextBlockGeneratorSignature, PrivateKeyAccount nextBlockGenerator) throws DataException {
		BigInteger hashValue = calcNextBlockHash(nextBlockVersion, nextBlockGeneratorSignature, nextBlockGenerator);
		BigInteger target = calcGeneratorsTarget(nextBlockGenerator);

		// If target is zero then generator has no balance so return longest value
		if (target.compareTo(BigInteger.ZERO) == 0)
			return Long.MAX_VALUE;

		// Use ratio of "correct guesses" to calculate minimum delay until this generator can forge a block
		BigInteger seconds = hashValue.divide(target).add(BigInteger.ONE);

		// Calculate next block timestamp using delay
		BigInteger timestamp = seconds.multiply(BigInteger.valueOf(1000)).add(BigInteger.valueOf(this.blockData.getTimestamp()));

		// Limit timestamp to maximum long value
		timestamp = timestamp.min(BigInteger.valueOf(Long.MAX_VALUE));

		return timestamp.longValue();
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
			this.transactions.add(Transaction.fromData(this.repository, transactionData));

		return this.transactions;
	}

	// Navigation

	/**
	 * Load parent block's data from repository via this block's reference.
	 * 
	 * @return parent's BlockData, or null if no parent found
	 * @throws DataException
	 */
	public BlockData getParent() throws DataException {
		byte[] reference = this.blockData.getReference();
		if (reference == null)
			return null;

		return this.repository.getBlockRepository().fromSignature(reference);
	}

	/**
	 * Load child block's data from repository via this block's signature.
	 * 
	 * @return child's BlockData, or null if no parent found
	 * @throws DataException
	 */
	public BlockData getChild() throws DataException {
		byte[] signature = this.blockData.getSignature();
		if (signature == null)
			return null;

		return this.repository.getBlockRepository().fromReference(signature);
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

		if (this.blockData.getGeneratorSignature() == null)
			throw new IllegalStateException("Cannot calculate transactions signature as block has no generator signature");

		// Check there is space in block
		try {
			if (BlockTransformer.getDataLength(this) + TransactionTransformer.getDataLength(transactionData) > MAX_BLOCK_BYTES)
				return false;
		} catch (TransformationException e) {
			return false;
		}

		// Add to block
		this.transactions.add(Transaction.fromData(this.repository, transactionData));

		// Update transaction count
		this.blockData.setTransactionCount(this.blockData.getTransactionCount() + 1);

		// Update totalFees
		this.blockData.setTotalFees(this.blockData.getTotalFees().add(transactionData.getFee()));

		calcTransactionsSignature();

		return true;
	}

	/**
	 * Recalculate block's generator signature.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount}.
	 * <p>
	 * Generator signature is made by the generator signing the following data:
	 * <p>
	 * previous block's generator signature + this block's generating balance + generator's public key
	 * <p>
	 * (Previous block's generator signature is extracted from this block's reference).
	 * 
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 * @throws RuntimeException
	 *             if somehow the generator signature cannot be calculated
	 */
	protected void calcGeneratorSignature() {
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
	protected void calcTransactionsSignature() {
		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		try {
			this.blockData.setTransactionsSignature(((PrivateKeyAccount) this.generator).sign(BlockTransformer.getBytesForTransactionsSignature(this)));
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to calculate block's transactions signature", e);
		}
	}

	public void sign() {
		this.calcGeneratorSignature();
		this.calcTransactionsSignature();

		this.blockData.setSignature(this.getSignature());
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
	 * Checks block's transactions by testing their validity then processing them.<br>
	 * Hence <b>calls repository.discardChanges()</b> before returning.
	 * 
	 * @return ValidationResult.OK if block is valid, or some other ValidationResult otherwise.
	 * @throws DataException
	 */
	public ValidationResult isValid() throws DataException {
		// TODO

		// Check parent block exists
		if (this.blockData.getReference() == null)
			return ValidationResult.REFERENCE_MISSING;

		BlockData parentBlockData = this.repository.getBlockRepository().fromSignature(this.blockData.getReference());
		if (parentBlockData == null)
			return ValidationResult.PARENT_DOES_NOT_EXIST;

		Block parentBlock = new Block(this.repository, parentBlockData);

		// Check timestamp is newer than parent timestamp
		if (this.blockData.getTimestamp() <= parentBlockData.getTimestamp())
			return ValidationResult.TIMESTAMP_OLDER_THAN_PARENT;

		// Check timestamp is not in the future (within configurable ~500ms margin)
		if (this.blockData.getTimestamp() - BlockChain.BLOCK_TIMESTAMP_MARGIN > NTP.getTime())
			return ValidationResult.TIMESTAMP_IN_FUTURE;

		// Legacy gen1 test: check timestamp ms is the same as parent timestamp ms?
		if (this.blockData.getTimestamp() % 1000 != parentBlockData.getTimestamp() % 1000)
			return ValidationResult.TIMESTAMP_MS_INCORRECT;

		// Check block version
		if (this.blockData.getVersion() != parentBlock.getNextBlockVersion())
			return ValidationResult.VERSION_INCORRECT;
		if (this.blockData.getVersion() < 2 && (this.blockData.getAtBytes() != null || this.blockData.getAtFees() != null))
			return ValidationResult.FEATURE_NOT_YET_RELEASED;

		// Check generating balance
		if (this.blockData.getGeneratingBalance().compareTo(parentBlock.calcNextBlockGeneratingBalance()) != 0)
			return ValidationResult.GENERATING_BALANCE_INCORRECT;

		// Check generator is allowed to forge this block at this time
		BigInteger hashValue = this.calcBlockHash();
		BigInteger target = parentBlock.calcGeneratorsTarget(this.generator);

		// Multiply target by guesses
		long guesses = (this.blockData.getTimestamp() - parentBlockData.getTimestamp()) / 1000;
		BigInteger lowerTarget = target.multiply(BigInteger.valueOf(guesses - 1));
		target = target.multiply(BigInteger.valueOf(guesses));

		// Generator's target must exceed block's hashValue threshold
		if (hashValue.compareTo(target) >= 0)
			return ValidationResult.GENERATOR_NOT_ACCEPTED;

		// XXX Odd gen1 test: "CHECK IF FIRST BLOCK OF USER"
		// Is the comment wrong? Does each second elapsed allows generator to test a new "target" window against hashValue?
		if (hashValue.compareTo(lowerTarget) < 0)
			return ValidationResult.GENERATOR_NOT_ACCEPTED;

		// Process CIYAM ATs, prepending AT-Transactions to block then compare post-execution checksums
		// XXX We should pre-calculate, and cache, next block's AT-transactions after processing each block to save repeated work
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
					return ValidationResult.GENESIS_TRANSACTIONS_INVALID;

				// Check timestamp and deadline
				if (transaction.getTransactionData().getTimestamp() > this.blockData.getTimestamp()
						|| transaction.getDeadline() <= this.blockData.getTimestamp())
					return ValidationResult.TRANSACTION_TIMESTAMP_INVALID;

				// Check transaction is even valid
				// NOTE: in Gen1 there was an extra block height passed to DeployATTransaction.isValid
				Transaction.ValidationResult validationResult = transaction.isValid();
				if (validationResult != Transaction.ValidationResult.OK) {
					LOGGER.error("Error during transaction validation, tx " + Base58.encode(transaction.getTransactionData().getSignature()) + ": "
							+ validationResult.value);
					return ValidationResult.TRANSACTION_INVALID;
				}

				// Process transaction to make sure other transactions validate properly
				try {
					transaction.process();
				} catch (Exception e) {
					LOGGER.error("Exception during transaction validation, tx " + Base58.encode(transaction.getTransactionData().getSignature()), e);
					e.printStackTrace();
					return ValidationResult.TRANSACTION_PROCESSING_FAILED;
				}
			}
		} catch (DataException e) {
			return ValidationResult.TRANSACTION_TIMESTAMP_INVALID;
		} finally {
			// Discard changes to repository made by test-processing transactions above
			try {
				this.repository.discardChanges();
			} catch (DataException e) {
				/*
				 * discardChanges failure most likely due to prior DataException, so catch discardChanges' DataException and ignore. Prior DataException
				 * propagates to caller.
				 */
			}
		}

		// Block is valid
		return ValidationResult.OK;
	}

	public void process() throws DataException {
		// Process transactions (we'll link them to this block after saving the block itself)
		List<Transaction> transactions = this.getTransactions();
		for (Transaction transaction : transactions)
			transaction.process();

		// If fees are non-zero then add fees to generator's balance
		BigDecimal blockFee = this.blockData.getTotalFees();
		if (blockFee.compareTo(BigDecimal.ZERO) > 0)
			this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).add(blockFee));

		// Link block into blockchain by fetching signature of highest block and setting that as our reference
		int blockchainHeight = this.repository.getBlockRepository().getBlockchainHeight();
		BlockData latestBlockData = this.repository.getBlockRepository().fromHeight(blockchainHeight);
		if (latestBlockData != null)
			this.blockData.setReference(latestBlockData.getSignature());

		this.blockData.setHeight(blockchainHeight + 1);
		this.repository.getBlockRepository().save(this.blockData);

		// Link transactions to this block, thus removing them from unconfirmed transactions list.
		for (int sequence = 0; sequence < transactions.size(); ++sequence) {
			Transaction transaction = transactions.get(sequence);

			// Link transaction to this block
			BlockTransactionData blockTransactionData = new BlockTransactionData(this.getSignature(), sequence,
					transaction.getTransactionData().getSignature());
			this.repository.getBlockRepository().save(blockTransactionData);
		}
	}

	public void orphan() throws DataException {
		// TODO

		// Orphan block's CIYAM ATs
		orphanAutomatedTransactions();

		// Orphan transactions in reverse order, and unlink them from this block
		List<Transaction> transactions = this.getTransactions();
		for (int sequence = transactions.size() - 1; sequence >= 0; --sequence) {
			Transaction transaction = transactions.get(sequence);
			transaction.orphan();

			BlockTransactionData blockTransactionData = new BlockTransactionData(this.getSignature(), sequence,
					transaction.getTransactionData().getSignature());
			this.repository.getBlockRepository().delete(blockTransactionData);
		}

		// If fees are non-zero then remove fees from generator's balance
		BigDecimal blockFee = this.blockData.getTotalFees();
		if (blockFee.compareTo(BigDecimal.ZERO) > 0)
			this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).subtract(blockFee));

		// Delete block from blockchain
		this.repository.getBlockRepository().delete(this.blockData);
	}

	public void orphanAutomatedTransactions() throws DataException {
		// TODO - CIYAM AT support
		/*
		 * LinkedHashMap< Tuple2<Integer, Integer> , AT_Transaction > atTxs = DBSet.getInstance().getATTransactionMap().getATTransactions(this.getHeight(db));
		 * 
		 * Iterator<AT_Transaction> iter = atTxs.values().iterator();
		 * 
		 * while ( iter.hasNext() ) { AT_Transaction key = iter.next(); Long amount = key.getAmount(); if (key.getRecipientId() != null &&
		 * !Arrays.equals(key.getRecipientId(), new byte[ AT_Constants.AT_ID_SIZE ]) && !key.getRecipient().equalsIgnoreCase("1") ) { Account recipient = new
		 * Account( key.getRecipient() ); recipient.setConfirmedBalance( recipient.getConfirmedBalance( db ).subtract( BigDecimal.valueOf( amount, 8 ) ) , db );
		 * if ( Arrays.equals(recipient.getLastReference(db),new byte[64])) { recipient.removeReference(db); } } Account sender = new Account( key.getSender()
		 * ); sender.setConfirmedBalance( sender.getConfirmedBalance( db ).add( BigDecimal.valueOf( amount, 8 ) ) , db );
		 * 
		 * }
		 */
	}

}
