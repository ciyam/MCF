package qora.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DB;
import database.NoDataFoundException;
import database.SaveHelper;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.assets.Order;
import qora.assets.Trade;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import utils.Base58;
import utils.NTP;
import utils.ParseException;
import utils.Serialization;

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

	/**
	 * Ordered list of columns when fetching a Block row from database.
	 */
	private static final String DB_COLUMNS = "version, reference, transaction_count, total_fees, "
			+ "transactions_signature, height, generation, generating_balance, generator, generator_signature, AT_data, AT_fees";

	// Database properties
	protected int version;
	protected byte[] reference;
	protected int transactionCount;
	protected BigDecimal totalFees;
	protected byte[] transactionsSignature;
	protected int height;
	protected long timestamp;
	protected BigDecimal generatingBalance;
	protected PublicKeyAccount generator;
	protected byte[] generatorSignature;
	protected byte[] atBytes;
	protected BigDecimal atFees;

	// Other properties
	protected List<Transaction> transactions;
	protected BigDecimal cachedNextGeneratingBalance;

	// Property lengths for serialisation
	protected static final int VERSION_LENGTH = 4;
	protected static final int TRANSACTIONS_SIGNATURE_LENGTH = 64;
	protected static final int GENERATOR_SIGNATURE_LENGTH = 64;
	protected static final int REFERENCE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	protected static final int TIMESTAMP_LENGTH = 8;
	protected static final int GENERATING_BALANCE_LENGTH = 8;
	protected static final int GENERATOR_LENGTH = 32;
	protected static final int TRANSACTION_COUNT_LENGTH = 4;
	protected static final int BASE_LENGTH = VERSION_LENGTH + REFERENCE_LENGTH + TIMESTAMP_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH
			+ TRANSACTIONS_SIGNATURE_LENGTH + GENERATOR_SIGNATURE_LENGTH + TRANSACTION_COUNT_LENGTH;

	// Other length constants
	protected static final int BLOCK_SIGNATURE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	public static final int MAX_BLOCK_BYTES = 1048576;
	protected static final int TRANSACTION_SIZE_LENGTH = 4; // per transaction
	protected static final int AT_BYTES_LENGTH = 4;
	protected static final int AT_FEES_LENGTH = 8;
	protected static final int AT_LENGTH = AT_FEES_LENGTH + AT_BYTES_LENGTH;

	// Other useful constants
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

	// Constructors

	// For creating a new block from scratch
	public Block(int version, byte[] reference, long timestamp, BigDecimal generatingBalance, PublicKeyAccount generator, byte[] atBytes, BigDecimal atFees) {
		this.version = version;
		this.reference = reference;
		this.timestamp = timestamp;
		this.generatingBalance = generatingBalance;
		this.generator = generator;
		this.generatorSignature = null;
		this.height = 0;

		this.transactionCount = 0;
		this.transactions = new ArrayList<Transaction>();
		this.transactionsSignature = null;
		this.totalFees = BigDecimal.ZERO.setScale(8);

		this.atBytes = atBytes;
		this.atFees = atFees;
		if (this.atFees != null)
			this.totalFees = this.totalFees.add(this.atFees);
	}

	// For instantiating a block that was previously serialized
	protected Block(int version, byte[] reference, long timestamp, BigDecimal generatingBalance, PublicKeyAccount generator, byte[] generatorSignature,
			byte[] transactionsSignature, byte[] atBytes, BigDecimal atFees, List<Transaction> transactions) {
		this(version, reference, timestamp, generatingBalance, generator, atBytes, atFees);

		this.generatorSignature = generatorSignature;

		this.transactionsSignature = transactionsSignature;
		this.transactionCount = transactions.size();
		this.transactions = transactions;

		// Add transactions' fees to totalFees
		for (Transaction transaction : this.transactions)
			this.totalFees = this.totalFees.add(transaction.getFee());
	}

	// Getters/setters

	public int getVersion() {
		return this.version;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public BigDecimal getGeneratingBalance() {
		return this.generatingBalance;
	}

	public PublicKeyAccount getGenerator() {
		return this.generator;
	}

	public byte[] getGeneratorSignature() {
		return this.generatorSignature;
	}

	public byte[] getTransactionsSignature() {
		return this.transactionsSignature;
	}

	public BigDecimal getTotalFees() {
		return this.totalFees;
	}

	public int getTransactionCount() {
		return this.transactionCount;
	}

	public byte[] getATBytes() {
		return this.atBytes;
	}

	public BigDecimal getATFees() {
		return this.atFees;
	}

	public int getHeight() {
		return this.height;
	}

	// More information

	/**
	 * Return composite block signature (generatorSignature + transactionsSignature).
	 * 
	 * @return byte[], or null if either component signature is null.
	 */
	public byte[] getSignature() {
		if (this.generatorSignature == null || this.transactionsSignature == null)
			return null;

		return Bytes.concat(this.generatorSignature, this.transactionsSignature);
	}

	public int getDataLength() {
		int blockLength = BASE_LENGTH;

		if (version >= 2 && this.atBytes != null)
			blockLength += AT_FEES_LENGTH + AT_BYTES_LENGTH + this.atBytes.length;

		// Short cut for no transactions
		if (this.transactions == null || this.transactions.isEmpty())
			return blockLength;

		for (Transaction transaction : this.transactions)
			blockLength += TRANSACTION_SIZE_LENGTH + transaction.getDataLength();

		return blockLength;
	}

	/**
	 * Return the next block's version.
	 * 
	 * @return 1, 2 or 3
	 */
	public int getNextBlockVersion() {
		if (this.height < AT_BLOCK_HEIGHT_RELEASE)
			return 1;
		else if (this.timestamp < POWFIX_RELEASE_TIMESTAMP)
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
		if (this.height % BLOCK_RETARGET_INTERVAL != 0)
			return this.generatingBalance;

		// Return cached calculation if we have one
		if (this.cachedNextGeneratingBalance != null)
			return this.cachedNextGeneratingBalance;

		// Perform calculation

		// Navigate back to first block in previous interval:
		// XXX: why can't we simply load using block height?
		Block firstBlock = this;
		for (int i = 1; firstBlock != null && i < BLOCK_RETARGET_INTERVAL; ++i)
			firstBlock = firstBlock.getParent();

		// Couldn't navigate back far enough?
		if (firstBlock == null)
			throw new IllegalStateException("Failed to calculate next block's generating balance due to lack of historic blocks");

		// Calculate the actual time period (in ms) over previous interval's blocks.
		long previousGeneratingTime = this.timestamp - firstBlock.getTimestamp();

		// Calculate expected forging time (in ms) for a whole interval based on this block's generating balance.
		long expectedGeneratingTime = Block.calcForgingDelay(this.generatingBalance) * BLOCK_RETARGET_INTERVAL * 1000;

		// Finally, scale generating balance such that faster than expected previous intervals produce larger generating balances.
		BigDecimal multiplier = BigDecimal.valueOf((double) expectedGeneratingTime / (double) previousGeneratingTime);
		this.cachedNextGeneratingBalance = BlockChain.minMaxBalance(this.generatingBalance.multiply(multiplier));

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
	 * If the block was loaded from DB then it's possible this method will call the DB to load the transactions if they are not already loaded.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public List<Transaction> getTransactions() throws SQLException {
		// Already loaded?
		if (this.transactions != null)
			return this.transactions;

		// Allocate cache for results
		this.transactions = new ArrayList<Transaction>();

		ResultSet rs = DB.checkedExecute("SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ?", this.getSignature());
		if (rs == null)
			return this.transactions; // No transactions in this block

		// NB: do-while loop because DB.checkedExecute() implicitly calls ResultSet.next() for us
		do {
			byte[] transactionSignature = DB.getResultSetBytes(rs.getBinaryStream(1), Transaction.SIGNATURE_LENGTH);
			this.transactions.add(TransactionFactory.fromSignature(transactionSignature));

			// No need to update totalFees as this will be loaded via the Blocks table
		} while (rs.next());

		// The number of transactions fetched from database should correspond with Block's transactionCount
		if (this.transactions.size() != this.transactionCount)
			throw new IllegalStateException("Block's transactions from database do not match block's transaction count");

		return this.transactions;
	}

	// Load/Save

	protected Block(byte[] signature) throws SQLException {
		this(DB.checkedExecute("SELECT " + DB_COLUMNS + " FROM Blocks WHERE signature = ?", signature));
	}

	protected Block(ResultSet rs) throws SQLException {
		if (rs == null)
			throw new NoDataFoundException();

		this.version = rs.getInt(1);
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(2), REFERENCE_LENGTH);
		this.transactionCount = rs.getInt(3);
		this.totalFees = rs.getBigDecimal(4);
		this.transactionsSignature = DB.getResultSetBytes(rs.getBinaryStream(5), TRANSACTIONS_SIGNATURE_LENGTH);
		this.height = rs.getInt(6);
		this.timestamp = rs.getTimestamp(7).getTime();
		this.generatingBalance = rs.getBigDecimal(8);
		// Note: can't use GENERATOR_LENGTH in case we encounter Genesis Account's short, 8-byte public key
		this.generator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(9)));
		this.generatorSignature = DB.getResultSetBytes(rs.getBinaryStream(10), GENERATOR_SIGNATURE_LENGTH);
		this.atBytes = DB.getResultSetBytes(rs.getBinaryStream(11));
		this.atFees = rs.getBigDecimal(12);
	}

	/**
	 * Load Block from DB using block signature.
	 * 
	 * @param signature
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromSignature(byte[] signature) throws SQLException {
		try {
			return new Block(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	/**
	 * Load Block from DB using block height
	 * 
	 * @param height
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromHeight(int height) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			PreparedStatement preparedStatement = connection.prepareStatement("SELECT " + DB_COLUMNS + " FROM Blocks WHERE height = ?");
			preparedStatement.setInt(1, height);

			try {
				return new Block(DB.checkedExecute(preparedStatement));
			} catch (NoDataFoundException e) {
				return null;
			}
		}
	}

	protected void save(Connection connection) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "Blocks");

		saveHelper.bind("signature", this.getSignature()).bind("version", this.version).bind("reference", this.reference)
				.bind("transaction_count", this.transactionCount).bind("total_fees", this.totalFees).bind("transactions_signature", this.transactionsSignature)
				.bind("height", this.height).bind("generation", new Timestamp(this.timestamp)).bind("generating_balance", this.generatingBalance)
				.bind("generator", this.generator.getPublicKey()).bind("generator_signature", this.generatorSignature).bind("AT_data", this.atBytes)
				.bind("AT_fees", this.atFees);

		saveHelper.execute();
	}

	// Navigation

	/**
	 * Load parent Block from DB
	 * 
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public Block getParent() throws SQLException {
		try {
			return new Block(this.reference);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	/**
	 * Load child Block from DB
	 * 
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public Block getChild() throws SQLException {
		byte[] blockSignature = this.getSignature();
		if (blockSignature == null)
			return null;

		ResultSet resultSet = DB.checkedExecute("SELECT " + DB_COLUMNS + " FROM Blocks WHERE reference = ?", blockSignature);

		try {
			return new Block(resultSet);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	// Converters

	@SuppressWarnings("unchecked")
	public JSONObject toJSON() throws SQLException {
		JSONObject json = new JSONObject();

		json.put("version", this.version);
		json.put("timestamp", this.timestamp);
		json.put("generatingBalance", this.generatingBalance);
		json.put("generator", this.generator.getAddress());
		json.put("generatorPublicKey", Base58.encode(this.generator.getPublicKey()));
		json.put("fee", this.getTotalFees().toPlainString());
		json.put("transactionsSignature", Base58.encode(this.transactionsSignature));
		json.put("generatorSignature", Base58.encode(this.generatorSignature));
		json.put("signature", Base58.encode(this.getSignature()));

		if (this.reference != null)
			json.put("reference", Base58.encode(this.reference));

		json.put("height", this.getHeight());

		// Add transaction info
		JSONArray transactionsJson = new JSONArray();
		boolean tradesHappened = false;

		for (Transaction transaction : this.getTransactions()) {
			transactionsJson.add(transaction.toJSON());

			// If this is an asset CreateOrderTransaction then check to see if any trades happened
			if (transaction.getType() == Transaction.TransactionType.CREATE_ASSET_ORDER) {
				CreateOrderTransaction orderTransaction = (CreateOrderTransaction) transaction;
				Order order = orderTransaction.getOrder();
				List<Trade> trades = order.getTrades();

				// Filter out trades with timestamps that don't match order transaction's timestamp
				trades.removeIf((Trade trade) -> trade.getTimestamp() != order.getTimestamp());

				// Any trades left?
				if (!trades.isEmpty()) {
					tradesHappened = true;

					// No need to check any further
					break;
				}
			}
		}
		json.put("transactions", transactionsJson);

		// Add asset trade activity flag
		json.put("assetTrades", tradesHappened);

		// Add CIYAM AT info (if any)
		if (atBytes != null) {
			json.put("blockATs", HashCode.fromBytes(atBytes).toString());
			json.put("atFees", this.atFees);
		}

		return json;
	}

	public byte[] toBytes() throws SQLException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.version));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(this.reference);
			// NOTE: generatingBalance serialized as long value, not as BigDecimal, for historic compatibility
			bytes.write(Longs.toByteArray(this.generatingBalance.longValue()));
			bytes.write(this.generator.getPublicKey());
			bytes.write(this.transactionsSignature);
			bytes.write(this.generatorSignature);

			if (this.version >= 2) {
				if (this.atBytes != null) {
					bytes.write(Ints.toByteArray(this.atBytes.length));
					bytes.write(this.atBytes);
					// NOTE: atFees serialized as long value, not as BigDecimal, for historic compatibility
					bytes.write(Longs.toByteArray(this.atFees.longValue()));
				} else {
					bytes.write(Ints.toByteArray(0));
					bytes.write(Longs.toByteArray(0L));
				}
			}

			// Transactions
			bytes.write(Ints.toByteArray(this.transactionCount));

			for (Transaction transaction : this.getTransactions()) {
				bytes.write(Ints.toByteArray(transaction.getDataLength()));
				bytes.write(transaction.toBytes());
			}

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Block parse(byte[] data) throws ParseException {
		if (data == null)
			return null;

		if (data.length < BASE_LENGTH)
			throw new ParseException("Byte data too short for Block");

		ByteBuffer byteBuffer = ByteBuffer.wrap(data);

		int version = byteBuffer.getInt();

		if (version >= 2 && data.length < BASE_LENGTH + AT_LENGTH)
			throw new ParseException("Byte data too short for V2+ Block");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		BigDecimal generatingBalance = BigDecimal.valueOf(byteBuffer.getLong()).setScale(8);
		PublicKeyAccount generator = Serialization.deserializePublicKey(byteBuffer);

		byte[] transactionsSignature = new byte[TRANSACTIONS_SIGNATURE_LENGTH];
		byteBuffer.get(transactionsSignature);
		byte[] generatorSignature = new byte[GENERATOR_SIGNATURE_LENGTH];
		byteBuffer.get(generatorSignature);

		byte[] atBytes = null;
		BigDecimal atFees = null;
		if (version >= 2) {
			int atBytesLength = byteBuffer.getInt();

			if (atBytesLength > MAX_BLOCK_BYTES)
				throw new ParseException("Byte data too long for Block's AT info");

			atBytes = new byte[atBytesLength];
			byteBuffer.get(atBytes);

			atFees = BigDecimal.valueOf(byteBuffer.getLong()).setScale(8);
		}

		int transactionCount = byteBuffer.getInt();

		// Parse transactions now, compared to deferred parsing in Gen1, so we can throw ParseException if need be
		List<Transaction> transactions = new ArrayList<Transaction>();
		for (int t = 0; t < transactionCount; ++t) {
			if (byteBuffer.remaining() < TRANSACTION_SIZE_LENGTH)
				throw new ParseException("Byte data too short for Block Transaction length");

			int transactionLength = byteBuffer.getInt();
			if (byteBuffer.remaining() < transactionLength)
				throw new ParseException("Byte data too short for Block Transaction");
			if (transactionLength > MAX_BLOCK_BYTES)
				throw new ParseException("Byte data too long for Block Transaction");

			byte[] transactionBytes = new byte[transactionLength];
			byteBuffer.get(transactionBytes);

			Transaction transaction = Transaction.parse(transactionBytes);
			transactions.add(transaction);
		}

		if (byteBuffer.hasRemaining())
			throw new ParseException("Excess byte data found after parsing Block");

		return new Block(version, reference, timestamp, generatingBalance, generator, generatorSignature, transactionsSignature, atBytes, atFees, transactions);
	}

	// Processing

	/**
	 * Add a transaction to the block.
	 * <p>
	 * Used when constructing a new block during forging.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount} so block's transactions signature can be recalculated.
	 * 
	 * @param transaction
	 * @return true if transaction successfully added to block, false otherwise
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 */
	public boolean addTransaction(Transaction transaction) {
		// Can't add to transactions if we haven't loaded existing ones yet
		if (this.transactions == null)
			throw new IllegalStateException("Attempted to add transaction to partially loaded database Block");

		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		// Check there is space in block
		if (this.getDataLength() + transaction.getDataLength() > MAX_BLOCK_BYTES)
			return false;

		// Add to block
		this.transactions.add(transaction);

		// Update transaction count
		this.transactionCount++;

		// Update totalFees
		this.totalFees.add(transaction.getFee());

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
	 */
	public void calcGeneratorSignature() {
		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		this.generatorSignature = ((PrivateKeyAccount) this.generator).sign(this.getBytesForGeneratorSignature());
	}

	private byte[] getBytesForGeneratorSignature() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH);

			// Only copy the generator signature from reference, which is the first 64 bytes.
			bytes.write(Arrays.copyOf(this.reference, GENERATOR_SIGNATURE_LENGTH));

			bytes.write(Longs.toByteArray(this.generatingBalance.longValue()));

			// We're padding here just in case the generator is the genesis account whose public key is only 8 bytes long.
			bytes.write(Bytes.ensureCapacity(this.generator.getPublicKey(), GENERATOR_LENGTH, 0));

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Recalculate block's transactions signature.
	 * <p>
	 * Requires block's {@code generator} being a {@code PrivateKeyAccount}.
	 * 
	 * @throws IllegalStateException
	 *             if block's {@code generator} is not a {@code PrivateKeyAccount}.
	 */
	public void calcTransactionsSignature() {
		if (!(this.generator instanceof PrivateKeyAccount))
			throw new IllegalStateException("Block's generator has no private key");

		this.transactionsSignature = ((PrivateKeyAccount) this.generator).sign(this.getBytesForTransactionsSignature());
	}

	private byte[] getBytesForTransactionsSignature() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + this.transactionCount * Transaction.SIGNATURE_LENGTH);

		try {
			bytes.write(this.generatorSignature);

			for (Transaction transaction : this.getTransactions()) {
				if (!transaction.isSignatureValid())
					return null;

				bytes.write(transaction.getSignature());
			}

			return bytes.toByteArray();
		} catch (IOException | SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isSignatureValid() {
		// Check generator's signature first
		if (!this.generator.verify(this.generatorSignature, getBytesForGeneratorSignature()))
			return false;

		// Check transactions signature
		if (!this.generator.verify(this.transactionsSignature, getBytesForTransactionsSignature()))
			return false;

		return true;
	}

	/**
	 * Returns whether Block is valid. Expected to be called within SQL Transaction.
	 * <p>
	 * Performs various tests like checking for parent block, correct block timestamp, version, generating balance, etc.<br>
	 * Also checks block's transactions using an HSQLDB "SAVEPOINT" and hence needs to be called within an ongoing SQL Transaction.
	 * 
	 * @param connection
	 * @return true if block is valid, false otherwise.
	 * @throws SQLException
	 */
	public boolean isValid(Connection connection) throws SQLException {
		// TODO

		// Check parent blocks exists
		if (this.reference == null)
			return false;

		Block parentBlock = this.getParent();
		if (parentBlock == null)
			return false;

		// Check timestamp is valid, i.e. later than parent timestamp and not in the future, within ~500ms margin
		if (this.timestamp < parentBlock.getTimestamp() || this.timestamp - BLOCK_TIMESTAMP_MARGIN > NTP.getTime())
			return false;

		// Legacy gen1 test: check timestamp ms is the same as parent timestamp ms?
		if (this.timestamp % 1000 != parentBlock.getTimestamp() % 1000)
			return false;

		// Check block version
		if (this.version != parentBlock.getNextBlockVersion())
			return false;
		if (this.version < 2 && (this.atBytes != null || this.atBytes.length > 0 || this.atFees != null || this.atFees.compareTo(BigDecimal.ZERO) > 0))
			return false;

		// Check generating balance
		if (this.generatingBalance != parentBlock.getNextBlockGeneratingBalance())
			return false;

		// Check generator's proof of stake against block's generating balance
		// TODO

		// Check CIYAM AT
		if (this.atBytes != null && this.atBytes.length > 0) {
			// TODO
			// try {
			// AT_Block atBlock = AT_Controller.validateATs(this.getBlockATs(), BlockChain.getHeight() + 1);
			// this.atFees = atBlock.getTotalFees();
			// } catch (NoSuchAlgorithmException | AT_Exception e) {
			// return false;
			// }
		}

		// Check transactions
		DB.createSavepoint(connection, "BLOCK_TRANSACTIONS");
		// XXX: we might need to catch SQLExceptions and not rollback which could cause a new exception?
		// OR: catch, attempt to rollback and then re-throw caught exception?
		// OR: don't catch, attempt to rollback, catch exception during rollback then return false?
		try {
			for (Transaction transaction : this.getTransactions()) {
				// GenesisTransactions are not allowed (GenesisBlock overrides isValid() to allow them)
				if (transaction instanceof GenesisTransaction)
					return false;

				// Check timestamp and deadline
				if (transaction.getTimestamp() > this.timestamp || transaction.getDeadline() <= this.timestamp)
					return false;

				// Check transaction is even valid
				// NOTE: in Gen1 there was an extra block height passed to DeployATTransaction.isValid
				if (transaction.isValid(connection) != Transaction.ValidationResult.OK)
					return false;

				// Process transaction to make sure other transactions validate properly
				try {
					transaction.process(connection);
				} catch (Exception e) {
					// LOGGER.error("Exception during transaction processing, tx " + Base58.encode(transaction.getSignature()), e);
					return false;
				}
			}
		} finally {
			// Revert back to savepoint
			DB.rollbackToSavepoint(connection, "BLOCK_TRANSACTIONS");
		}

		// Block is valid
		return true;
	}

	public void process(Connection connection) throws SQLException {
		// Process transactions (we'll link them to this block after saving the block itself)
		List<Transaction> transactions = this.getTransactions();
		for (Transaction transaction : transactions)
			transaction.process(connection);

		// If fees are non-zero then add fees to generator's balance
		BigDecimal blockFee = this.getTotalFees();
		if (blockFee.compareTo(BigDecimal.ZERO) == 1)
			this.generator.setConfirmedBalance(connection, Asset.QORA, this.generator.getConfirmedBalance(Asset.QORA).add(blockFee));

		// Link block into blockchain by fetching signature of highest block and setting that as our reference
		int blockchainHeight = BlockChain.getHeight();
		Block latestBlock = Block.fromHeight(blockchainHeight);
		if (latestBlock != null)
			this.reference = latestBlock.getSignature();

		this.height = blockchainHeight + 1;
		this.save(connection);

		// Link transactions to this block, thus removing them from unconfirmed transactions list.
		for (int sequence = 0; sequence < transactions.size(); ++sequence) {
			Transaction transaction = transactions.get(sequence);

			// Link transaction to this block
			BlockTransaction blockTransaction = new BlockTransaction(this.getSignature(), sequence, transaction.getSignature());
			blockTransaction.save(connection);
		}
	}

	public void orphan(Connection connection) {
		// TODO
	}

}
