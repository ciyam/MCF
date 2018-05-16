package qora.block;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import com.google.common.primitives.Bytes;

import database.DB;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.crypto.Crypto;
import qora.transaction.Transaction;

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

	// Database properties shared with all block types
	protected int version;
	protected byte[] reference;
	protected int transactionCount;
	protected BigDecimal totalFees;
	protected byte[] transactionsSignature;
	protected int height;
	protected long timestamp;
	protected BigDecimal generationTarget;
	protected String generator;
	protected byte[] generationSignature;
	protected byte[] atBytes;
	protected BigDecimal atFees;

	// Property lengths for serialisation
	protected static final int VERSION_LENGTH = 4;
	protected static final int REFERENCE_LENGTH = 64;
	protected static final int TIMESTAMP_LENGTH = 8;
	protected static final int GENERATION_TARGET_LENGTH = 8;
	protected static final int GENERATOR_LENGTH = 32;
	protected static final int TRANSACTIONS_SIGNATURE_LENGTH = 64;
	protected static final int GENERATION_SIGNATURE_LENGTH = 64;
	protected static final int TRANSACTION_COUNT_LENGTH = 8;
	protected static final int BASE_LENGTH = VERSION_LENGTH + REFERENCE_LENGTH + TIMESTAMP_LENGTH + GENERATION_TARGET_LENGTH + GENERATOR_LENGTH
			+ TRANSACTIONS_SIGNATURE_LENGTH + GENERATION_SIGNATURE_LENGTH + TRANSACTION_COUNT_LENGTH;

	// Other length constants
	public static final int MAX_BLOCK_BYTES = 1048576;
	protected static final int TRANSACTION_SIZE_LENGTH = 4;
	public static final int MAX_TRANSACTION_BYTES = MAX_BLOCK_BYTES - BASE_LENGTH - TRANSACTION_SIZE_LENGTH;
	protected static final int AT_BYTES_LENGTH = 4;
	protected static final int AT_FEES_LENGTH = 8;
	protected static final int AT_LENGTH = AT_FEES_LENGTH + AT_BYTES_LENGTH;

	// Constructors
	protected Block(int version, byte[] reference, long timestamp, BigDecimal generationTarget, String generator, byte[] generationSignature, byte[] atBytes,
			BigDecimal atFees) {
		this.version = version;
		this.reference = reference;
		this.timestamp = timestamp;
		this.generationTarget = generationTarget;
		this.generator = generator;
		this.generationSignature = generationSignature;

		this.transactionCount = 0;
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

	public String getGenerator() {
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

	// More information

	public byte[] getSignature() {
		if (this.generationSignature == null || this.transactionsSignature == null)
			return null;

		return Bytes.concat(this.generationSignature, this.transactionsSignature);
	}

	public int getDataLength() {
		return 0;
	}

	// Load/Save

	protected Block(Connection connection, byte[] signature) throws SQLException {
		ResultSet rs = DB.executeUsingBytes(connection,
				"SELECT version, reference, transaction_count, total_fees, "
						+ "transactions_signature, height, generation, generation_target, generator, generation_signature, "
						+ "AT_data, AT_fees FROM Blocks WHERE signature = ?",
				signature);

		this.version = rs.getInt(1);
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(2), REFERENCE_LENGTH);
		this.transactionCount = rs.getInt(3);
		this.totalFees = rs.getBigDecimal(4);
		this.transactionsSignature = DB.getResultSetBytes(rs.getBinaryStream(5), TRANSACTIONS_SIGNATURE_LENGTH);
		this.height = rs.getInt(6);
		this.timestamp = rs.getTimestamp(7).getTime();
		this.generationTarget = rs.getBigDecimal(8);
		this.generator = rs.getString(9);
		this.generationSignature = DB.getResultSetBytes(rs.getBinaryStream(10), GENERATION_SIGNATURE_LENGTH);
		this.atBytes = DB.getResultSetBytes(rs.getBinaryStream(11));
		this.atFees = rs.getBigDecimal(12);
	}

	protected void save(Connection connection) throws SQLException {
		String sql = DB.formatInsertWithPlaceholders("Blocks", "version", "reference", "transaction_count", "total_fees", "transactions_signature", "height",
				"generation", "generation_target", "generator", "generation_signature", "AT_data", "AT_fees");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.version, this.reference, this.transactionCount, this.totalFees, this.transactionsSignature,
				this.height, this.timestamp, this.generationTarget, this.generator, this.generationSignature, this.atBytes, this.atFees);
		preparedStatement.execute();

		// Save transactions
		// Save transaction-block mappings
	}

	// Navigation

	// Converters

	public JSONObject toJSON() {
		return null;
	}

	public byte[] toBytes() {
		return null;
	}

	// Processing

	public boolean addTransaction(Transaction transaction) {
		// Check there is space in block
		// Add to block
		// Update transaction count
		// Update transactions signature
		return false; // no room
	}

	public byte[] calcSignature(PrivateKeyAccount signer) {
		byte[] bytes = this.toBytes();

		return Crypto.sign(signer, bytes);
	}

	public boolean isSignatureValid(PublicKeyAccount signer) {
		return false;
	}

	public int isValid() {
		return VALIDATE_OK;
	}

	public void process() {
	}

	public void orphan() {
	}

}
