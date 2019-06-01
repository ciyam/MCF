package org.qora.block;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.Forging;
import org.qora.account.PrivateKeyAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.at.AT;
import org.qora.block.BlockChain.RewardByHeight;
import org.qora.crypto.Crypto;
import org.qora.data.account.ProxyForgerData;
import org.qora.data.at.ATData;
import org.qora.data.at.ATStateData;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.ATRepository;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.AtTransaction;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.transform.Transformer;
import org.qora.transform.block.BlockTransformer;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;
import org.qora.utils.NTP;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

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
		PARENT_HAS_EXISTING_CHILD(13),
		TIMESTAMP_OLDER_THAN_PARENT(20),
		TIMESTAMP_IN_FUTURE(21),
		TIMESTAMP_MS_INCORRECT(22),
		TIMESTAMP_TOO_SOON(23),
		VERSION_INCORRECT(30),
		FEATURE_NOT_YET_RELEASED(31),
		GENERATING_BALANCE_INCORRECT(40),
		GENERATOR_NOT_ACCEPTED(41),
		GENESIS_TRANSACTIONS_INVALID(50),
		TRANSACTION_TIMESTAMP_INVALID(51),
		TRANSACTION_INVALID(52),
		TRANSACTION_PROCESSING_FAILED(53),
		TRANSACTION_ALREADY_PROCESSED(54),
		TRANSACTION_NEEDS_APPROVAL(55),
		AT_STATES_MISMATCH(61);

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

	/** Sorted list of transactions attached to this block */
	protected List<Transaction> transactions;

	/** Remote/imported/loaded AT states */
	protected List<ATStateData> atStates;
	/** Locally-generated AT states */
	protected List<ATStateData> ourAtStates;
	/** Locally-generated AT fees */
	protected BigDecimal ourAtFees; // Generated locally

	/** Cached copy of next block's generating balance */
	protected BigDecimal cachedNextGeneratingBalance;
	/** Minimum Qora balance for use in calculations. */
	public static final BigDecimal MIN_BALANCE = BigDecimal.valueOf(1L).setScale(8);

	// Other useful constants

	/** Maximum size of block in bytes */
	// TODO push this out to blockchain config file
	public static final int MAX_BLOCK_BYTES = 1048576;

	private static final BigInteger MAX_DISTANCE;
	static {
		byte[] maxValue = new byte[Transformer.PUBLIC_KEY_LENGTH];
		Arrays.fill(maxValue, (byte) 0xFF);
		MAX_DISTANCE = new BigInteger(1, maxValue);
	}

	// Constructors

	/**
	 * Constructs Block-handling object without loading transactions and AT states.
	 * <p>
	 * Transactions and AT states are loaded on first call to getTransactions() or getATStates() respectively.
	 * 
	 * @param repository
	 * @param blockData
	 * @throws DataException
	 */
	public Block(Repository repository, BlockData blockData) throws DataException {
		this.repository = repository;
		this.blockData = blockData;
		this.generator = new PublicKeyAccount(repository, blockData.getGeneratorPublicKey());
	}

	/**
	 * Constructs Block-handling object using passed transaction and AT states.
	 * <p>
	 * This constructor typically used when receiving a serialized block over the network.
	 * 
	 * @param repository
	 * @param blockData
	 * @param transactions
	 * @param atStates
	 * @throws DataException
	 */
	public Block(Repository repository, BlockData blockData, List<TransactionData> transactions, List<ATStateData> atStates) throws DataException {
		this(repository, blockData);

		this.transactions = new ArrayList<Transaction>();

		BigDecimal totalFees = BigDecimal.ZERO.setScale(8);

		// We have to sum fees too
		for (TransactionData transactionData : transactions) {
			this.transactions.add(Transaction.fromData(repository, transactionData));
			totalFees = totalFees.add(transactionData.getFee());
		}

		this.atStates = atStates;
		for (ATStateData atState : atStates)
			totalFees = totalFees.add(atState.getFees());

		this.blockData.setTotalFees(totalFees);
	}

	/**
	 * Constructs Block-handling object with basic, initial values.
	 * <p>
	 * This constructor typically used when generating a new block.
	 * <p>
	 * Note that CIYAM ATs will be executed and AT-Transactions prepended to this block, along with AT state data and fees.
	 * 
	 * @param repository
	 * @param parentBlockData
	 * @param generator
	 * @param timestamp
	 * @throws DataException
	 */
	public Block(Repository repository, BlockData parentBlockData, PrivateKeyAccount generator, long timestamp) throws DataException {
		this.repository = repository;
		this.generator = generator;

		Block parentBlock = new Block(repository, parentBlockData);

		int version = parentBlock.getNextBlockVersion();
		byte[] reference = parentBlockData.getSignature();
		BigDecimal generatingBalance = parentBlock.calcNextBlockGeneratingBalance();
		int height = parentBlockData.getHeight() + 1;

		byte[] generatorSignature;
		try {
			Integer signatureHeight = version >= 4 ? height : null;
			generatorSignature = generator.sign(BlockTransformer.getBytesForGeneratorSignature(parentBlockData.getGeneratorSignature(), timestamp, signatureHeight, generator));
		} catch (TransformationException e) {
			throw new DataException("Unable to calculate next block generator signature", e);
		}

		int transactionCount = 0;
		byte[] transactionsSignature = null;

		this.transactions = new ArrayList<Transaction>();

		int atCount = 0;
		BigDecimal atFees = BigDecimal.ZERO.setScale(8);
		BigDecimal totalFees = atFees;

		// This instance used for AT processing
		this.blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generator.getPublicKey(), generatorSignature, atCount, atFees);

		// Requires this.blockData and this.transactions, sets this.ourAtStates and this.ourAtFees
		this.executeATs();

		atCount = this.ourAtStates.size();
		this.atStates = this.ourAtStates;
		atFees = this.ourAtFees;
		totalFees = atFees;

		// Rebuild blockData using post-AT-execute data
		this.blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generator.getPublicKey(), generatorSignature, atCount, atFees);
	}

	/**
	 * Construct another block using this block as template, but with different generator account.
	 * <p>
	 * NOTE: uses the same transactions list, AT states, etc.
	 * 
	 * @param generator
	 * @return
	 * @throws DataException
	 */
	public Block regenerate(PrivateKeyAccount generator) throws DataException {
		Block newBlock = new Block(this.repository, this.blockData);
		newBlock.generator = generator;

		// Copy AT state data
		newBlock.ourAtStates = this.ourAtStates;
		newBlock.atStates = newBlock.ourAtStates;
		newBlock.ourAtFees = this.ourAtFees;

		int version = this.blockData.getVersion();
		byte[] reference = this.blockData.getReference();
		BigDecimal generatingBalance = this.blockData.getGeneratingBalance();
		long timestamp = this.blockData.getTimestamp();
		byte[] parentGeneratorSignature = BlockTransformer.getGeneratorSignatureFromReference(reference);
		int height = this.blockData.getHeight();

		byte[] generatorSignature;
		try {
			Integer signatureHeight = version >= 4 ? height : null;
			generatorSignature = generator.sign(BlockTransformer.getBytesForGeneratorSignature(parentGeneratorSignature, timestamp, signatureHeight, generator));
		} catch (TransformationException e) {
			throw new DataException("Unable to calculate next block generator signature", e);
		}

		newBlock.transactions = this.transactions;
		int transactionCount = this.blockData.getTransactionCount();
		BigDecimal totalFees = this.blockData.getTotalFees();
		byte[] transactionsSignature = null; // We'll calculate this later

		int atCount = newBlock.ourAtStates.size();
		BigDecimal atFees = newBlock.ourAtFees;

		newBlock.blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generator.getPublicKey(), generatorSignature, atCount, atFees);

		// Resign to update transactions signature
		newBlock.sign();

		return newBlock;
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
	 * @return 1, 2, 3 or 4
	 */
	public int getNextBlockVersion() {
		if (this.blockData.getHeight() == null)
			throw new IllegalStateException("Can't determine next block's version as this block has no height set");

		if (this.blockData.getHeight() < BlockChain.getInstance().getATReleaseHeight())
			return 1;
		else if (this.blockData.getTimestamp() < BlockChain.getInstance().getPowFixReleaseTimestamp())
			return 2;
		else if (this.blockData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			return 3;
		else
			return 4;
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
		if (this.blockData.getHeight() == null)
			throw new IllegalStateException("Can't calculate next block's generating balance as this block's height is unset");

		// This block not at the start of an interval?
		if (this.blockData.getHeight() % BlockChain.getInstance().getBlockDifficultyInterval() != 0)
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
			for (int i = 1; firstBlock != null && i < BlockChain.getInstance().getBlockDifficultyInterval(); ++i)
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
		long expectedGeneratingTime = Block.calcForgingDelay(this.blockData.getGeneratingBalance()) * BlockChain.getInstance().getBlockDifficultyInterval()
				* 1000;

		// Finally, scale generating balance such that faster than expected previous intervals produce larger generating balances.
		// NOTE: we have to use doubles and longs here to keep compatibility with Qora v1 results
		double multiplier = (double) expectedGeneratingTime / (double) previousGeneratingTime;
		long nextGeneratingBalance = (long) (this.blockData.getGeneratingBalance().doubleValue() * multiplier);

		this.cachedNextGeneratingBalance = Block.minMaxBalance(BigDecimal.valueOf(nextGeneratingBalance).setScale(8));

		return this.cachedNextGeneratingBalance;
	}

	/**
	 * Return expected forging delay, in seconds, since previous block based on passed generating balance.
	 */
	public static long calcForgingDelay(BigDecimal generatingBalance) {
		generatingBalance = Block.minMaxBalance(generatingBalance);

		double percentageOfTotal = generatingBalance.divide(BlockChain.getInstance().getMaxBalance()).doubleValue();
		long actualBlockTime = (long) (BlockChain.getInstance().getMinBlockTime()
				+ ((BlockChain.getInstance().getMaxBlockTime() - BlockChain.getInstance().getMinBlockTime()) * (1 - percentageOfTotal)));

		return actualBlockTime;
	}

	/**
	 * Return block's transactions.
	 * <p>
	 * If the block was loaded from repository then it's possible this method will call the repository to fetch the transactions if not done already.
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

	/**
	 * Return block's AT states.
	 * <p>
	 * If the block was loaded from repository then it's possible this method will call the repository to fetch the AT states if not done already.
	 * <p>
	 * <b>Note:</b> AT states fetched from repository only contain summary info, not actual data like serialized state data or AT creation timestamps!
	 * 
	 * @return
	 * @throws DataException
	 */
	public List<ATStateData> getATStates() throws DataException {
		// Already loaded?
		if (this.atStates != null)
			return this.atStates;

		// If loading from repository, this block must have a height
		if (this.blockData.getHeight() == null)
			throw new IllegalStateException("Can't fetch block's AT states from repository without a block height");

		// Allocate cache for results
		List<ATStateData> atStateData = this.repository.getATRepository().getBlockATStatesAtHeight(this.blockData.getHeight());

		// The number of AT states fetched from repository should correspond with Block's atCount
		if (atStateData.size() != this.blockData.getATCount())
			throw new IllegalStateException("Block's AT states from repository do not match block's AT count");

		this.atStates = atStateData;

		return this.atStates;
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

		// Already added? (Check using signature)
		if (this.transactions.stream().anyMatch(transaction -> Arrays.equals(transaction.getTransactionData().getSignature(), transactionData.getSignature())))
			return true;

		// Check there is space in block
		try {
			if (BlockTransformer.getDataLength(this) + TransactionTransformer.getDataLength(transactionData) > MAX_BLOCK_BYTES)
				return false;
		} catch (TransformationException e) {
			return false;
		}

		// Add to block
		this.transactions.add(Transaction.fromData(this.repository, transactionData));

		// Re-sort
		this.transactions.sort(Transaction.getComparator());

		// Update transaction count
		this.blockData.setTransactionCount(this.blockData.getTransactionCount() + 1);

		// Update totalFees
		this.blockData.setTotalFees(this.blockData.getTotalFees().add(transactionData.getFee()));

		// We've added a transaction, so recalculate transactions signature
		calcTransactionsSignature();

		return true;
	}

	/**
	 * Remove a transaction from the block.
	 * <p>
	 * Used when constructing a new block during forging.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount} so block's transactions signature can be recalculated.
	 * 
	 * @param transactionData
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 */
	public void deleteTransaction(TransactionData transactionData) {
		// Can't add to transactions if we haven't loaded existing ones yet
		if (this.transactions == null)
			throw new IllegalStateException("Attempted to add transaction to partially loaded database Block");

		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		if (this.blockData.getGeneratorSignature() == null)
			throw new IllegalStateException("Cannot calculate transactions signature as block has no generator signature");

		// Attempt to remove from block (Check using signature)
		boolean wasElementRemoved = this.transactions.removeIf(transaction -> Arrays.equals(transaction.getTransactionData().getSignature(), transactionData.getSignature()));
		if (!wasElementRemoved)
			// Wasn't there - nothing more to do
			return;

		// Re-sort
		this.transactions.sort(Transaction.getComparator());

		// Update transaction count
		this.blockData.setTransactionCount(this.blockData.getTransactionCount() - 1);

		// Update totalFees
		this.blockData.setTotalFees(this.blockData.getTotalFees().subtract(transactionData.getFee()));

		// We've removed a transaction, so recalculate transactions signature
		calcTransactionsSignature();
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

	public static byte[] calcIdealGeneratorPublicKey(int height, byte[] blockSignature) {
		return Crypto.digest(Bytes.concat(Longs.toByteArray(height), blockSignature));
	}

	public static byte[] calcHeightPerturbedGenerator(int height, byte[] generatorPublicKey) {
		return Crypto.digest(Bytes.concat(Longs.toByteArray(height), generatorPublicKey));
	}

	public static BigInteger calcGeneratorDistance(BlockData parentBlockData, byte[] generatorPublicKey) {
		BigInteger idealGeneratorBI = new BigInteger(Block.calcIdealGeneratorPublicKey(parentBlockData.getHeight(), parentBlockData.getSignature()));
		BigInteger ourGeneratorBI = new BigInteger(Block.calcHeightPerturbedGenerator(parentBlockData.getHeight() + 1, generatorPublicKey));
		return idealGeneratorBI.subtract(ourGeneratorBI).abs();
	}

	public BigInteger calcGeneratorDistance(BlockData parentBlockData) {
		return calcGeneratorDistance(parentBlockData, this.generator.getPublicKey());
	}

	/**
	 * Returns timestamp based on previous block and this block's generator.
	 * <p>
	 * Uses same proportion of this block's generator from 'ideal' generator
	 * with min to max target block periods, added to previous block's timestamp.
	 * <p>
	 * Example:<br>
	 * This block's generator is 20% of max distance from 'ideal' generator.<br>
	 * Min/Max block periods are 30s and 90s respectively.<br>
	 * 20% of (90s - 30s) is 12s<br>
	 * So this block's timestamp is previous block's timestamp + 30s + 12s.
	 */
	public static long calcMinimumTimestamp(BlockData parentBlockData, byte[] generatorPublicKey) {
		BigInteger distance = calcGeneratorDistance(parentBlockData, generatorPublicKey);

		long minBlockTime = BlockChain.getInstance().getMinBlockTime(); // seconds
		long maxBlockTime = BlockChain.getInstance().getMaxBlockTime(); // seconds

		long timeOffset = distance.multiply(BigInteger.valueOf((maxBlockTime - minBlockTime) * 1000L)).divide(MAX_DISTANCE).longValue();

		return parentBlockData.getTimestamp() + (minBlockTime * 1000L) + timeOffset;
	}

	public long calcMinimumTimestamp(BlockData parentBlockData) {
		return calcMinimumTimestamp(parentBlockData, this.generator.getPublicKey());
	}

	/**
	 * Recalculate block's generator and transactions signatures, thus giving block full signature.
	 * <p>
	 * Note: Block instance must have been constructed with a <tt>PrivateKeyAccount generator</tt> or this call will throw an <tt>IllegalStateException</tt>.
	 * 
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 */
	public void sign() {
		this.calcGeneratorSignature();
		this.calcTransactionsSignature();

		this.blockData.setSignature(this.getSignature());
	}

	/**
	 * Returns whether this block's signatures are valid.
	 * 
	 * @return true if both generator and transaction signatures are valid, false otherwise
	 */
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
	 * Returns whether Block's timestamp is valid.
	 * <p>
	 * Used by BlockGenerator to check whether it's time to forge new block,
	 * and also used by Block.isValid for checks (if not testnet).
	 * 
	 * @return ValidationResult.OK if timestamp valid, or some other ValidationResult otherwise.
	 * @throws DataException
	 */
	public ValidationResult isTimestampValid() throws DataException {
		BlockData parentBlockData = this.repository.getBlockRepository().fromSignature(this.blockData.getReference());
		if (parentBlockData == null)
			return ValidationResult.PARENT_DOES_NOT_EXIST;

		// Check timestamp is newer than parent timestamp
		if (this.blockData.getTimestamp() <= parentBlockData.getTimestamp())
			return ValidationResult.TIMESTAMP_OLDER_THAN_PARENT;

		// Check timestamp is not in the future (within configurable ~500ms margin)
		if (this.blockData.getTimestamp() - BlockChain.getInstance().getBlockTimestampMargin() > NTP.getTime())
			return ValidationResult.TIMESTAMP_IN_FUTURE;

		// Check timestamp is at least minimum based on parent block
		if (this.blockData.getTimestamp() < this.calcMinimumTimestamp(parentBlockData))
			return ValidationResult.TIMESTAMP_TOO_SOON;

		return ValidationResult.OK;
	}

	/**
	 * Returns whether Block is valid.
	 * <p>
	 * Performs various tests like checking for parent block, correct block timestamp, version, generating balance, etc.
	 * <p>
	 * Checks block's transactions by testing their validity then processing them.<br>
	 * Hence uses a repository savepoint during execution.
	 * 
	 * @return ValidationResult.OK if block is valid, or some other ValidationResult otherwise.
	 * @throws DataException
	 */
	public ValidationResult isValid() throws DataException {
		// Check parent block exists
		if (this.blockData.getReference() == null)
			return ValidationResult.REFERENCE_MISSING;

		BlockData parentBlockData = this.repository.getBlockRepository().fromSignature(this.blockData.getReference());
		if (parentBlockData == null)
			return ValidationResult.PARENT_DOES_NOT_EXIST;

		Block parentBlock = new Block(this.repository, parentBlockData);

		// Check parent doesn't already have a child block
		if (parentBlock.getChild() != null)
			return ValidationResult.PARENT_HAS_EXISTING_CHILD;

		// Check timestamp is newer than parent timestamp
		if (this.blockData.getTimestamp() <= parentBlockData.getTimestamp())
			return ValidationResult.TIMESTAMP_OLDER_THAN_PARENT;

		// These checks are disabled for testnet
		if (!BlockChain.getInstance().isTestNet()) {
			ValidationResult timestampResult = this.isTimestampValid();

			if (timestampResult != ValidationResult.OK)
				return timestampResult;
		}

		// Check block version
		if (this.blockData.getVersion() != parentBlock.getNextBlockVersion())
			return ValidationResult.VERSION_INCORRECT;
		if (this.blockData.getVersion() < 2 && this.blockData.getATCount() != 0)
			return ValidationResult.FEATURE_NOT_YET_RELEASED;

		// Check generator is allowed to forge this block
		if (!isGeneratorValidToForge(parentBlock))
			return ValidationResult.GENERATOR_NOT_ACCEPTED;

		// CIYAM ATs
		if (this.blockData.getATCount() != 0) {
			// Locally generated AT states should be valid so no need to re-execute them
			if (this.ourAtStates != this.getATStates()) {
				// For old v1 CIYAM ATs we blindly accept them
				if (this.blockData.getVersion() < 4) {
					this.ourAtStates = this.atStates;
					this.ourAtFees = this.blockData.getATFees();
				} else {
					// Generate local AT states for comparison
					this.executeATs();
				}

				// Check locally generated AT states against ones received from elsewhere

				if (this.ourAtStates.size() != this.blockData.getATCount())
					return ValidationResult.AT_STATES_MISMATCH;

				if (this.ourAtFees.compareTo(this.blockData.getATFees()) != 0)
					return ValidationResult.AT_STATES_MISMATCH;

				// Note: this.atStates fully loaded thanks to this.getATStates() call above
				for (int s = 0; s < this.atStates.size(); ++s) {
					ATStateData ourAtState = this.ourAtStates.get(s);
					ATStateData theirAtState = this.atStates.get(s);

					if (!ourAtState.getATAddress().equals(theirAtState.getATAddress()))
						return ValidationResult.AT_STATES_MISMATCH;

					if (!ourAtState.getStateHash().equals(theirAtState.getStateHash()))
						return ValidationResult.AT_STATES_MISMATCH;

					if (ourAtState.getFees().compareTo(theirAtState.getFees()) != 0)
						return ValidationResult.AT_STATES_MISMATCH;
				}
			}
		}

		// Check transactions
		try {
			// Create repository savepoint here so we can rollback to it after testing transactions
			repository.setSavepoint();

			for (Transaction transaction : this.getTransactions()) {
				TransactionData transactionData = transaction.getTransactionData();

				// GenesisTransactions are not allowed (GenesisBlock overrides isValid() to allow them)
				if (transactionData.getType() == TransactionType.GENESIS || transactionData.getType() == TransactionType.ACCOUNT_FLAGS)
					return ValidationResult.GENESIS_TRANSACTIONS_INVALID;

				// Check timestamp and deadline
				if (transactionData.getTimestamp() > this.blockData.getTimestamp()
						|| transaction.getDeadline() <= this.blockData.getTimestamp())
					return ValidationResult.TRANSACTION_TIMESTAMP_INVALID;

				// Check transaction isn't already included in a block
				if (this.repository.getTransactionRepository().isConfirmed(transactionData.getSignature()))
					return ValidationResult.TRANSACTION_ALREADY_PROCESSED;

				// Check transaction has correct reference, etc.
				if (!transaction.hasValidReference()) {
					LOGGER.debug("Error during transaction validation, tx " + Base58.encode(transactionData.getSignature()) + ": INVALID_REFERENCE");
					return ValidationResult.TRANSACTION_INVALID;
				}

				// Check transaction is even valid
				// NOTE: in Gen1 there was an extra block height passed to DeployATTransaction.isValid
				Transaction.ValidationResult validationResult = transaction.isValid();
				if (validationResult != Transaction.ValidationResult.OK) {
					LOGGER.debug("Error during transaction validation, tx " + Base58.encode(transactionData.getSignature()) + ": "
							+ validationResult.name());
					return ValidationResult.TRANSACTION_INVALID;
				}

				// Check transaction can even be processed
				validationResult = transaction.isProcessable();
				if (validationResult != Transaction.ValidationResult.OK) {
					LOGGER.debug("Error during transaction validation, tx " + Base58.encode(transactionData.getSignature()) + ": "
							+ validationResult.name());
					return ValidationResult.TRANSACTION_INVALID;
				}

				// Process transaction to make sure other transactions validate properly
				try {
					// Only process transactions that don't require group-approval.
					// Group-approval transactions are dealt with later.
					if (transactionData.getApprovalStatus() == ApprovalStatus.NOT_REQUIRED)
						transaction.process();

					// Regardless of group-approval, update relevant info for creator (e.g. lastReference)
					transaction.processReferencesAndFees();
				} catch (Exception e) {
					LOGGER.error("Exception during transaction validation, tx " + Base58.encode(transactionData.getSignature()), e);
					e.printStackTrace();
					return ValidationResult.TRANSACTION_PROCESSING_FAILED;
				}
			}
		} catch (DataException e) {
			return ValidationResult.TRANSACTION_INVALID;
		} finally {
			// Rollback repository changes made by test-processing transactions above
			try {
				this.repository.rollbackToSavepoint();
			} catch (DataException e) {
				/*
				 * Rollback failure most likely due to prior DataException, so discard this DataException. Prior DataException propagates to caller.
				 */
			}
		}

		// Block is valid
		return ValidationResult.OK;
	}

	/**
	 * Execute CIYAM ATs for this block.
	 * <p>
	 * This needs to be done locally for all blocks, regardless of origin.<br>
	 * Typically called by <tt>isValid()</tt> or new block constructor.
	 * <p>
	 * After calling, AT-generated transactions are prepended to the block's transactions and AT state data is generated.
	 * <p>
	 * Updates <tt>this.ourAtStates</tt> (local version) and <tt>this.ourAtFees</tt> (remote/imported/loaded version).
	 * <p>
	 * Note: this method does not store new AT state data into repository - that is handled by <tt>process()</tt>.
	 * <p>
	 * This method is not needed if fetching an existing block from the repository as AT state data will be loaded from repository as well.
	 * 
	 * @see #isValid()
	 * 
	 * @throws DataException
	 * 
	 */
	private void executeATs() throws DataException {
		// We're expecting a lack of AT state data at this point.
		if (this.ourAtStates != null)
			throw new IllegalStateException("Attempted to execute ATs when block's local AT state data already exists");

		// AT-Transactions generated by running ATs, to be prepended to block's transactions
		List<AtTransaction> allATTransactions = new ArrayList<AtTransaction>();

		this.ourAtStates = new ArrayList<ATStateData>();
		this.ourAtFees = BigDecimal.ZERO.setScale(8);

		// Find all executable ATs, ordered by earliest creation date first
		List<ATData> executableATs = this.repository.getATRepository().getAllExecutableATs();

		// Run each AT, appends AT-Transactions and corresponding AT states, to our lists
		for (ATData atData : executableATs) {
			AT at = new AT(this.repository, atData);
			List<AtTransaction> atTransactions = at.run(this.blockData.getTimestamp());

			allATTransactions.addAll(atTransactions);

			ATStateData atStateData = at.getATStateData();
			this.ourAtStates.add(atStateData);

			this.ourAtFees = this.ourAtFees.add(atStateData.getFees());
		}

		// Prepend our entire AT-Transactions/states to block's transactions
		this.transactions.addAll(0, allATTransactions);

		// Re-sort
		this.transactions.sort(Transaction.getComparator());

		// Update transaction count
		this.blockData.setTransactionCount(this.blockData.getTransactionCount() + 1);

		// We've added transactions, so recalculate transactions signature
		calcTransactionsSignature();
	}

	/** Returns whether block's generator is actually allowed to forge this block. */
	protected boolean isGeneratorValidToForge(Block parentBlock) throws DataException {
		// Generator must have forging flag enabled
		Account generator = new PublicKeyAccount(repository, this.blockData.getGeneratorPublicKey());
		if (Forging.canForge(generator))
			return true;

		// Check whether generator public key could be a proxy forge account
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(this.blockData.getGeneratorPublicKey());
		if (proxyForgerData != null) {
			Account forger = new PublicKeyAccount(this.repository, proxyForgerData.getForgerPublicKey());

			if (Forging.canForge(forger))
				return true;
		}

		return false;
	}

	/**
	 * Process block, and its transactions, adding them to the blockchain.
	 * 
	 * @throws DataException
	 */
	public void process() throws DataException {
		// Set our block's height
		int blockchainHeight = this.repository.getBlockRepository().getBlockchainHeight();
		this.blockData.setHeight(blockchainHeight + 1);

		// Block rewards go before transactions processed
		processBlockRewards();

		// Process transactions (we'll link them to this block after saving the block itself)
		// AT-generated transactions are already prepended to our transactions at this point.
		List<Transaction> transactions = this.getTransactions();
		for (Transaction transaction : transactions) {
			// AT_TRANSACTIONs are created locally and need saving into repository before processing
			if (transaction.getTransactionData().getType() == TransactionType.AT)
				this.repository.getTransactionRepository().save(transaction.getTransactionData());

			// Only process transactions that don't require group-approval.
			// Group-approval transactions are dealt with later.
			if (transaction.getTransactionData().getApprovalStatus() == ApprovalStatus.NOT_REQUIRED)
				transaction.process();

			// Regardless of group-approval, update relevant info for creator (e.g. lastReference)
			transaction.processReferencesAndFees();
		}

		// Group-approval transactions
		processGroupApprovalTransactions();

		// Give transaction fees to generator/proxy
		rewardTransactionFees();

		// Process AT fees and save AT states into repository
		ATRepository atRepository = this.repository.getATRepository();
		for (ATStateData atState : this.getATStates()) {
			Account atAccount = new Account(this.repository, atState.getATAddress());

			// Subtract AT-generated fees from AT accounts
			atAccount.setConfirmedBalance(Asset.QORA, atAccount.getConfirmedBalance(Asset.QORA).subtract(atState.getFees()));

			atRepository.save(atState);
		}

		// Link block into blockchain by fetching signature of highest block and setting that as our reference
		BlockData latestBlockData = this.repository.getBlockRepository().fromHeight(blockchainHeight);
		if (latestBlockData != null)
			this.blockData.setReference(latestBlockData.getSignature());

		this.repository.getBlockRepository().save(this.blockData);

		// Link transactions to this block, thus removing them from unconfirmed transactions list.
		// Also update "transaction participants" in repository for "transactions involving X" support in API
		for (int sequence = 0; sequence < transactions.size(); ++sequence) {
			Transaction transaction = transactions.get(sequence);

			// Link transaction to this block
			BlockTransactionData blockTransactionData = new BlockTransactionData(this.getSignature(), sequence,
					transaction.getTransactionData().getSignature());
			this.repository.getBlockRepository().save(blockTransactionData);

			// Update transaction's height in repository
			this.repository.getTransactionRepository().updateHeight(transaction.getTransactionData().getSignature(), this.blockData.getHeight());
			// Update local transactionData's height too
			transaction.getTransactionData().setBlockHeight(this.blockData.getHeight());

			// No longer unconfirmed
			this.repository.getTransactionRepository().confirmTransaction(transaction.getTransactionData().getSignature());

			List<Account> participants = transaction.getInvolvedAccounts();
			List<String> participantAddresses = participants.stream().map(account -> account.getAddress()).collect(Collectors.toList());
			this.repository.getTransactionRepository().saveParticipants(transaction.getTransactionData(), participantAddresses);
		}
	}

	protected void processGroupApprovalTransactions() throws DataException {
		// Search for pending transactions that have now expired
		List<TransactionData> approvalExpiringTransactions = this.repository.getTransactionRepository().getApprovalExpiringTransactions(this.blockData.getHeight());
		for (TransactionData transactionData : approvalExpiringTransactions) {
			transactionData.setApprovalStatus(ApprovalStatus.EXPIRED);
			this.repository.getTransactionRepository().save(transactionData);
		}

		// Search for pending transactions within min/max block delay range
		List<TransactionData> approvalPendingTransactions = this.repository.getTransactionRepository().getApprovalPendingTransactions(this.blockData.getHeight());
		for (TransactionData transactionData : approvalPendingTransactions) {
			Transaction transaction = Transaction.fromData(this.repository, transactionData);

			// something like:
			Boolean isApproved = transaction.getApprovalDecision();

			if (isApproved == null)
				continue; // approve/reject threshold not yet met

			if (!isApproved) {
				// REJECT
				transactionData.setApprovalStatus(ApprovalStatus.REJECTED);
				this.repository.getTransactionRepository().save(transactionData);
				continue;
			}

			// Approved, but check transaction can still be processed
			if (transaction.isProcessable() != Transaction.ValidationResult.OK) {
				transactionData.setApprovalStatus(ApprovalStatus.INVALID);
				this.repository.getTransactionRepository().save(transactionData);
				continue;
			}

			// APPROVED, in which case do transaction.process();
			transactionData.setApprovalStatus(ApprovalStatus.APPROVED);
			this.repository.getTransactionRepository().save(transactionData);

			transaction.process();
		}
	}

	protected void processBlockRewards() throws DataException {
		BigDecimal reward = getRewardAtHeight(this.blockData.getHeight());

		// No reward for our height?
		if (reward == null)
			return;

		// Is generator public key actually a proxy forge key?
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(this.blockData.getGeneratorPublicKey());
		if (proxyForgerData != null) {
			// Split reward between forger and recipient
			Account recipient = new Account(this.repository, proxyForgerData.getRecipient());
			BigDecimal recipientShare = reward.multiply(proxyForgerData.getShare().movePointLeft(2)).setScale(8, RoundingMode.DOWN);
			recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).add(recipientShare));

			Account forger = new PublicKeyAccount(this.repository, proxyForgerData.getForgerPublicKey());
			BigDecimal forgerShare = reward.subtract(recipientShare);
			forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).add(forgerShare));
			return;
		}

		// Give block reward to generator
		this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).add(reward));
	}

	protected void rewardTransactionFees() throws DataException {
		BigDecimal blockFees = this.blockData.getTotalFees();

		// No transaction fees?
		if (blockFees.compareTo(BigDecimal.ZERO) <= 0)
			return;

		// Is generator public key actually a proxy forge key?
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(this.blockData.getGeneratorPublicKey());
		if (proxyForgerData != null) {
			// Split fees between forger and recipient
			Account recipient = new Account(this.repository, proxyForgerData.getRecipient());
			BigDecimal recipientShare = blockFees.multiply(proxyForgerData.getShare().movePointLeft(2)).setScale(8, RoundingMode.DOWN);
			recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).add(recipientShare));

			Account forger = new PublicKeyAccount(this.repository, proxyForgerData.getForgerPublicKey());
			BigDecimal forgerShare = blockFees.subtract(recipientShare);
			forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).add(forgerShare));
			return;
		}

		// Give transaction fees to generator
		this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).add(blockFees));
	}

	/**
	 * Removes block from blockchain undoing transactions and adding them to unconfirmed pile.
	 * 
	 * @throws DataException
	 */
	public void orphan() throws DataException {
		// Orphan transactions in reverse order, and unlink them from this block
		// AT-generated transactions are already added to our transactions so no special handling is needed here.
		List<Transaction> transactions = this.getTransactions();
		for (int sequence = transactions.size() - 1; sequence >= 0; --sequence) {
			Transaction transaction = transactions.get(sequence);
			transaction.orphan();

			// Unlink transaction from this block
			BlockTransactionData blockTransactionData = new BlockTransactionData(this.getSignature(), sequence,
					transaction.getTransactionData().getSignature());
			this.repository.getBlockRepository().delete(blockTransactionData);

			// Add to unconfirmed pile, or delete if AT_TRANSACTION
			if (transaction.getTransactionData().getType() == TransactionType.AT)
				this.repository.getTransactionRepository().delete(transaction.getTransactionData());
			else
				this.repository.getTransactionRepository().unconfirmTransaction(transaction.getTransactionData());

			this.repository.getTransactionRepository().deleteParticipants(transaction.getTransactionData());
		}

		// Block rewards removed after transactions undone
		orphanBlockRewards();

		// Deduct any transaction fees from generator/proxy
		deductTransactionFees();

		// Return AT fees and delete AT states from repository
		ATRepository atRepository = this.repository.getATRepository();
		for (ATStateData atState : this.getATStates()) {
			Account atAccount = new Account(this.repository, atState.getATAddress());

			// Return AT-generated fees to AT accounts
			atAccount.setConfirmedBalance(Asset.QORA, atAccount.getConfirmedBalance(Asset.QORA).add(atState.getFees()));
		}
		// Delete ATStateData for this height
		atRepository.deleteATStates(this.blockData.getHeight());

		// Delete block from blockchain
		this.repository.getBlockRepository().delete(this.blockData);
	}

	protected void orphanBlockRewards() throws DataException {
		BigDecimal reward = getRewardAtHeight(this.blockData.getHeight());

		// No reward for our height?
		if (reward == null)
			return;

		// Is generator public key actually a proxy forge key?
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(this.blockData.getGeneratorPublicKey());
		if (proxyForgerData != null) {
			// Split reward between forger and recipient
			Account recipient = new Account(this.repository, proxyForgerData.getRecipient());
			BigDecimal recipientShare = reward.multiply(proxyForgerData.getShare().movePointLeft(2)).setScale(8, RoundingMode.DOWN);
			recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).subtract(recipientShare));

			Account forger = new PublicKeyAccount(this.repository, proxyForgerData.getForgerPublicKey());
			BigDecimal forgerShare = reward.subtract(recipientShare);
			forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).subtract(forgerShare));
			return;
		}

		// Take block reward from generator
		this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).subtract(reward));
	}

	protected void deductTransactionFees() throws DataException {
		BigDecimal blockFees = this.blockData.getTotalFees();

		// No transaction fees?
		if (blockFees.compareTo(BigDecimal.ZERO) <= 0)
			return;

		// Is generator public key actually a proxy forge key?
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(this.blockData.getGeneratorPublicKey());
		if (proxyForgerData != null) {
			// Split fees between forger and recipient
			Account recipient = new Account(this.repository, proxyForgerData.getRecipient());
			BigDecimal recipientShare = blockFees.multiply(proxyForgerData.getShare().movePointLeft(2)).setScale(8, RoundingMode.DOWN);
			recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).subtract(recipientShare));

			Account forger = new PublicKeyAccount(this.repository, proxyForgerData.getForgerPublicKey());
			BigDecimal forgerShare = blockFees.subtract(recipientShare);
			forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).subtract(forgerShare));
			return;
		}

		// Deduct transaction fees to generator
		this.generator.setConfirmedBalance(Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).subtract(blockFees));
	}

	protected BigDecimal getRewardAtHeight(int ourHeight) {
		List<RewardByHeight> rewardsByHeight = BlockChain.getInstance().getBlockRewardsByHeight();

		// No rewards configured?
		if (rewardsByHeight == null)
			return null;

		// Scan through for reward at our height
		for (int i = rewardsByHeight.size() - 1; i >= 0; --i)
			if (rewardsByHeight.get(i).height <= ourHeight)
				return rewardsByHeight.get(i).reward;

		return null;
	}

	/**
	 * Return Qora balance adjusted to within min/max limits.
	 */
	public static BigDecimal minMaxBalance(BigDecimal balance) {
		if (balance.compareTo(Block.MIN_BALANCE) < 0)
			return Block.MIN_BALANCE;

		if (balance.compareTo(BlockChain.getInstance().getMaxBalance()) > 0)
			return BlockChain.getInstance().getMaxBalance();

		return balance;
	}

}
