package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import org.json.simple.JSONObject;

import database.DB;
import database.NoDataFoundException;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.crypto.Crypto;
import settings.Settings;

public abstract class Transaction {

	// Transaction types
	public enum TransactionType {
		Genesis(1), Payment(2);

		public final int value;

		private final static Map<Integer, TransactionType> map = stream(TransactionType.values()).collect(toMap(type -> type.value, type -> type));

		TransactionType(int value) {
			this.value = value;
		}

		public static TransactionType valueOf(int value) {
			return map.get(value);
		}
	}

	// Validation results
	public static final int VALIDATE_OK = 1;

	// Minimum fee
	public static final BigDecimal MINIMUM_FEE = BigDecimal.ONE;

	// Cached info to make transaction processing faster
	protected static final BigDecimal maxBytePerFee = BigDecimal.valueOf(Settings.getInstance().getMaxBytePerFee());
	protected static final BigDecimal minFeePerByte = BigDecimal.ONE.divide(maxBytePerFee, MathContext.DECIMAL32);

	// Database properties shared with all transaction types
	protected TransactionType type;
	protected String creator;
	protected long timestamp;
	protected byte[] reference;
	protected BigDecimal fee;
	protected byte[] signature;

	// Derived/cached properties
	// maybe: protected PublicKeyAccount creatorAccount;
	
	// Property lengths
	protected static final int TYPE_LENGTH = 4;
	protected static final int TIMESTAMP_LENGTH = 8;
	protected static final int REFERENCE_LENGTH = 64;
	protected static final int FEE_LENGTH = 8;
	protected static final int SIGNATURE_LENGTH = 64;
	protected static final int BASE_TYPELESS_LENGTH = TIMESTAMP_LENGTH + REFERENCE_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH;

	// Constructors

	protected Transaction(TransactionType type, BigDecimal fee, String creator, long timestamp, byte[] reference, byte[] signature) {
		this.fee = fee;
		this.type = type;
		this.creator = creator;
		this.timestamp = timestamp;
		this.reference = reference;
		this.signature = signature;
	}

	protected Transaction(TransactionType type, BigDecimal fee, String creator, long timestamp, byte[] reference) {
		this(type, fee, creator, timestamp, reference, null);
	}

	// Getters/setters

	public TransactionType getType() {
		return this.type;
	}

	public String getCreator() {
		return this.creator;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public BigDecimal getFee() {
		return this.fee;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	// More information

	public long getDeadline() {
		// 24 hour deadline to include transaction in a block
		return this.timestamp + (24 * 60 * 60 * 1000);
	}

	public abstract int getDataLength();

	public boolean hasMinimumFee() {
		return this.fee.compareTo(MINIMUM_FEE) >= 0;
	}

	public BigDecimal feePerByte() {
		return this.fee.divide(new BigDecimal(this.getDataLength()), MathContext.DECIMAL32);
	}

	public boolean hasMinimumFeePerByte() {
		return this.feePerByte().compareTo(minFeePerByte) >= 0;
	}

	public BigDecimal calcRecommendedFee() {
		BigDecimal recommendedFee = BigDecimal.valueOf(this.getDataLength()).divide(maxBytePerFee, MathContext.DECIMAL32).setScale(8);

		// security margin
		recommendedFee = recommendedFee.add(new BigDecimal("0.0000001"));

		if (recommendedFee.compareTo(MINIMUM_FEE) <= 0) {
			recommendedFee = MINIMUM_FEE;
		} else {
			recommendedFee = recommendedFee.setScale(0, BigDecimal.ROUND_UP);
		}

		return recommendedFee.setScale(8);
	}

	// Load/Save

	protected Transaction(Connection connection, TransactionType type, byte[] signature) throws SQLException {
		ResultSet rs = DB.executeUsingBytes(connection, "SELECT reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.type = type;
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(1), REFERENCE_LENGTH);
		this.creator = rs.getString(2);
		this.timestamp = rs.getTimestamp(3).getTime();
		this.fee = rs.getBigDecimal(4).setScale(8);
		this.signature = signature;
	}

	protected void save(Connection connection) throws SQLException {
		String sql = DB.formatInsertWithPlaceholders("Transactions", "signature", "reference", "type", "creator", "creation", "fee", "milestone_block");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.signature, this.reference, this.type.value, this.creator,
				Timestamp.from(Instant.ofEpochSecond(this.timestamp)), this.fee, null);
		preparedStatement.execute();
	}

	// Navigation

	/*
	 * public Block getBlock() { BlockTransaction blockTx = BlockTransaction.fromTransactionSignature(this.signature); if (blockTx == null) return null;
	 * 
	 * return Block.fromSignature(blockTx.getSignature()); }
	 * 
	 */
	
	public Transaction getParent(Connection connection) throws SQLException {
		if (this.reference == null)
			return null;
	
		return TransactionFactory.fromSignature(connection, this.reference); 
	}

	public Transaction getChild(Connection connection) throws SQLException {
		if (this.signature == null)
			return null;
	
		return TransactionFactory.fromReference(connection, this.signature); 
	}

	// Converters

	public abstract JSONObject toJSON();

	public abstract byte[] toBytes();

	// Processing

	public byte[] calcSignature(PrivateKeyAccount signer) {
		byte[] bytes = this.toBytes();

		return Crypto.sign(signer, bytes);
	}

	public boolean isSignatureValid(PublicKeyAccount signer) {
		if (this.signature == null)
			return false;

		return Crypto.verify(signer.getPublicKey(), this.signature, this.toBytes());
	}

	public abstract int isValid();

	public abstract void process();

	public abstract void orphan();

}
