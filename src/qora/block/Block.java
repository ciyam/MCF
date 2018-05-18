package qora.block;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

import com.google.common.primitives.Bytes;

import database.DB;
import database.NoDataFoundException;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;

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
 * GenerationSignature's data is: reference + generationTarget + generator's public key
 * TransactionSignature's data is: generationSignature + transaction signatures
 * Block signature is: generationSignature + transactionsSignature
 */

public class Block {

	// Validation results
	public static final int VALIDATE_OK = 1;

	// Columns when fetching from database
	private static final String DB_COLUMNS = "version, reference, transaction_count, total_fees, "
			+ "transactions_signature, height, generation, generation_target, generator, generation_signature, " + "AT_data, AT_fees";

	// Database properties
	protected int version;
	protected byte[] reference;
	protected int transactionCount;
	protected BigDecimal totalFees;
	protected byte[] transactionsSignature;
	protected int height;
	protected long timestamp;
	protected BigDecimal generationTarget;
	protected PublicKeyAccount generator;
	protected byte[] generationSignature;
	protected byte[] atBytes;
	protected BigDecimal atFees;

	// Other properties
	protected List<Transaction> transactions;

	// Property lengths for serialisation
	protected static final int VERSION_LENGTH = 4;
	protected static final int TRANSACTIONS_SIGNATURE_LENGTH = 64;
	protected static final int GENERATION_SIGNATURE_LENGTH = 64;
	protected static final int REFERENCE_LENGTH = GENERATION_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	protected static final int TIMESTAMP_LENGTH = 8;
	protected static final int GENERATION_TARGET_LENGTH = 8;
	protected static final int GENERATOR_LENGTH = 32;
	protected static final int TRANSACTION_COUNT_LENGTH = 8;
	protected static final int BASE_LENGTH = VERSION_LENGTH + REFERENCE_LENGTH + TIMESTAMP_LENGTH + GENERATION_TARGET_LENGTH + GENERATOR_LENGTH
			+ TRANSACTIONS_SIGNATURE_LENGTH + GENERATION_SIGNATURE_LENGTH + TRANSACTION_COUNT_LENGTH;

	// Other length constants
	protected static final int BLOCK_SIGNATURE_LENGTH = GENERATION_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	public static final int MAX_BLOCK_BYTES = 1048576;
	protected static final int TRANSACTION_SIZE_LENGTH = 4; // per transaction
	public static final int MAX_TRANSACTION_BYTES = MAX_BLOCK_BYTES - BASE_LENGTH;
	protected static final int AT_BYTES_LENGTH = 4;
	protected static final int AT_FEES_LENGTH = 8;
	protected static final int AT_LENGTH = AT_FEES_LENGTH + AT_BYTES_LENGTH;

	// Constructors
	protected Block(int version, byte[] reference, long timestamp, BigDecimal generationTarget, PublicKeyAccount generator, byte[] generationSignature,
			byte[] atBytes, BigDecimal atFees) {
		this.version = version;
		this.reference = reference;
		this.timestamp = timestamp;
		this.generationTarget = generationTarget;
		this.generator = generator;
		this.generationSignature = generationSignature;
		this.height = 0;

		this.transactionCount = 0;
		this.transactions = null;
		this.transactionsSignature = null;
		this.totalFees = null;

		this.atBytes = atBytes;
		this.atFees = atFees;
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

	public BigDecimal getGenerationTarget() {
		return this.generationTarget;
	}

	public PublicKeyAccount getGenerator() {
		return this.generator;
	}

	public byte[] getGenerationSignature() {
		return this.generationSignature;
	}

	public byte[] getTransactionsSignature() {
		return this.transactionsSignature;
	}

	public BigDecimal getTotalFees() {
		return null;
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

	public byte[] getSignature() {
		if (this.generationSignature == null || this.transactionsSignature == null)
			return null;

		return Bytes.concat(this.generationSignature, this.transactionsSignature);
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

	public List<Transaction> getTransactions() {
		return this.transactions;
	}

	/**
	 * Return block's transactions from DB (or cache).
	 * <p>
	 * If block's transactions have already been loaded from DB then the cached copied is returned instead.
	 * 
	 * @param connection
	 * @return
	 * @throws SQLException
	 */
	public List<Transaction> getTransactions(Connection connection) throws SQLException {
		// Already loaded?
		if (this.transactions != null)
			return this.transactions;

		// Allocate cache for results
		this.transactions = new ArrayList<Transaction>();

		// Load from DB
		ResultSet rs = DB.executeUsingBytes(connection, "SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ?", this.getSignature());
		if (rs == null)
			return this.transactions; // No transactions in this block

		// Use each row's signature to load, and cache, Transactions
		// NB: do-while loop because DB.executeUsingBytes() implicitly calls ResultSet.next() for us
		do {
			byte[] transactionSignature = DB.getResultSetBytes(rs.getBinaryStream(1), Transaction.SIGNATURE_LENGTH);
			this.transactions.add(TransactionFactory.fromSignature(connection, transactionSignature));
		} while (rs.next());

		return this.transactions;
	}

	// Load/Save

	protected Block(Connection connection, byte[] signature) throws SQLException {
		this(DB.executeUsingBytes(connection, "SELECT " + DB_COLUMNS + " FROM Blocks WHERE signature = ?", signature));
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
		this.generationTarget = rs.getBigDecimal(8);
		this.generator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(9), GENERATOR_LENGTH));
		this.generationSignature = DB.getResultSetBytes(rs.getBinaryStream(10), GENERATION_SIGNATURE_LENGTH);
		this.atBytes = DB.getResultSetBytes(rs.getBinaryStream(11));
		this.atFees = rs.getBigDecimal(12);
	}

	/**
	 * Load Block from DB using block signature.
	 * 
	 * @param connection
	 * @param signature
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromSignature(Connection connection, byte[] signature) throws SQLException {
		try {
			return new Block(connection, signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	/**
	 * Load Block from DB using block height
	 * 
	 * @param connection
	 * @param height
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromHeight(Connection connection, int height) throws SQLException {
		PreparedStatement preparedStatement = connection.prepareStatement("SELECT signature FROM Blocks WHERE height = ?");
		preparedStatement.setInt(1, height);

		try {
			return new Block(DB.checkedExecute(preparedStatement));
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	protected void save(Connection connection) throws SQLException {
		String sql = DB.formatInsertWithPlaceholders("Blocks", "version", "reference", "transaction_count", "total_fees", "transactions_signature", "height",
				"generation", "generation_target", "generator", "generation_signature", "AT_data", "AT_fees");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.version, this.reference, this.transactionCount, this.totalFees, this.transactionsSignature,
				this.height, this.timestamp, this.generationTarget, this.generator.getPublicKey(), this.generationSignature, this.atBytes, this.atFees);
		preparedStatement.execute();

		// Save transactions
		// Save transaction-block mappings
	}

	// Navigation

	/**
	 * Load parent Block from DB
	 * 
	 * @param connection
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public Block getParent(Connection connection) throws SQLException {
		try {
			return new Block(connection, this.reference);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	/**
	 * Load child Block from DB
	 * 
	 * @param connection
	 * @return Block, or null if not found
	 * @throws SQLException
	 */
	public Block getChild(Connection connection) throws SQLException {
		byte[] blockSignature = this.getSignature();
		if (blockSignature == null)
			return null;

		ResultSet resultSet = DB.executeUsingBytes(connection, "SELECT " + DB_COLUMNS + " FROM Blocks WHERE reference = ?", blockSignature);

		try {
			return new Block(resultSet);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	// Converters

	public JSONObject toJSON() {
		// TODO
		return null;
	}

	public byte[] toBytes() {
		// TODO
		return null;
	}

	// Processing

	public boolean addTransaction(Transaction transaction) {
		// TODO
		// Check there is space in block
		// Add to block
		// Update transaction count
		// Update transactions signature
		return false; // no room
	}

	public byte[] calcSignature(PrivateKeyAccount signer) {
		// TODO
		return null;
	}

	public boolean isSignatureValid(PublicKeyAccount signer) {
		// TODO
		return false;
	}

	public boolean isValid(Connection connection) throws SQLException {
		// TODO
		return false;
	}

	public void process() {
		// TODO
	}

	public void orphan() {
		// TODO
	}

}
