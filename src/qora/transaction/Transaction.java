package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import org.json.simple.JSONObject;

import database.DB;
import database.NoDataFoundException;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.block.BlockChain;
import qora.block.BlockTransaction;
import settings.Settings;

import utils.Base58;

public abstract class Transaction {

	// Transaction types
	public enum TransactionType {
		GENESIS(1), PAYMENT(2), REGISTER_NAME(3), UPDATE_NAME(4), SELL_NAME(5), CANCEL_SELL_NAME(6), BUY_NAME(7), CREATE_POLL(8), VOTE_ON_POLL(9), ARBITRARY(
				10), ISSUE_ASSET(11), TRANSFER_ASSET(12), CREATE_ASSET_ORDER(13), CANCEL_ASSET_ORDER(14), MULTIPAYMENT(15), DEPLOY_AT(16), MESSAGE(17);

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
	public enum ValidationResult {
		OK(1), INVALID_ADDRESS(2), NEGATIVE_AMOUNT(3);

		public final int value;

		private final static Map<Integer, ValidationResult> map = stream(ValidationResult.values()).collect(toMap(result -> result.value, result -> result));

		ValidationResult(int value) {
			this.value = value;
		}

		public static ValidationResult valueOf(int value) {
			return map.get(value);
		}
	}

	// Minimum fee
	public static final BigDecimal MINIMUM_FEE = BigDecimal.ONE;

	// Cached info to make transaction processing faster
	protected static final BigDecimal maxBytePerFee = BigDecimal.valueOf(Settings.getInstance().getMaxBytePerFee());
	protected static final BigDecimal minFeePerByte = BigDecimal.ONE.divide(maxBytePerFee, MathContext.DECIMAL32);

	// Database properties shared with all transaction types
	protected TransactionType type;
	protected PublicKeyAccount creator;
	protected long timestamp;
	protected byte[] reference;
	protected BigDecimal fee;
	protected byte[] signature;

	// Derived/cached properties

	// Property lengths for serialisation
	protected static final int TYPE_LENGTH = 4;
	protected static final int TIMESTAMP_LENGTH = 8;
	protected static final int REFERENCE_LENGTH = 64;
	protected static final int FEE_LENGTH = 8;
	public static final int SIGNATURE_LENGTH = 64;
	protected static final int BASE_TYPELESS_LENGTH = TIMESTAMP_LENGTH + REFERENCE_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH;

	// Other length constants
	public static final int CREATOR_LENGTH = 32;
	public static final int RECIPIENT_LENGTH = 25;

	// Constructors

	protected Transaction(TransactionType type, BigDecimal fee, PublicKeyAccount creator, long timestamp, byte[] reference, byte[] signature) {
		this.fee = fee;
		this.type = type;
		this.creator = creator;
		this.timestamp = timestamp;
		this.reference = reference;
		this.signature = signature;
	}

	protected Transaction(TransactionType type, BigDecimal fee, PublicKeyAccount creator, long timestamp, byte[] reference) {
		this(type, fee, creator, timestamp, reference, null);
	}

	// Getters/setters

	public TransactionType getType() {
		return this.type;
	}

	public PublicKeyAccount getCreator() {
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

	/**
	 * Get block height for this transaction in the blockchain.
	 * 
	 * @return height, or 0 if not in blockchain (i.e. unconfirmed)
	 * @throws SQLException
	 */
	public int getHeight() throws SQLException {
		if (this.signature == null)
			return 0;

		BlockTransaction blockTx = BlockTransaction.fromTransactionSignature(this.signature);
		if (blockTx == null)
			return 0;

		return BlockChain.getBlockHeightFromSignature(blockTx.getBlockSignature());
	}

	/**
	 * Get number of confirmations for this transaction.
	 * 
	 * @return confirmation count, or 0 if not in blockchain (i.e. unconfirmed)
	 * @throws SQLException
	 */
	public int getConfirmations() throws SQLException {
		int ourHeight = this.getHeight();
		if (ourHeight == 0)
			return 0;

		int blockChainHeight = BlockChain.getMaxHeight();
		return blockChainHeight - ourHeight + 1;
	}

	// Load/Save

	// Typically called by sub-class' load-from-DB constructors

	/**
	 * Load base Transaction from DB using signature.
	 * <p>
	 * Note that the transaction type is <b>not</b> checked against the DB's value.
	 * 
	 * @param type
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected Transaction(TransactionType type, byte[] signature) throws SQLException {
		ResultSet rs = DB.executeUsingBytes("SELECT reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.type = type;
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(1), REFERENCE_LENGTH);
		this.creator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(2)));
		this.timestamp = rs.getTimestamp(3).getTime();
		this.fee = rs.getBigDecimal(4).setScale(8);
		this.signature = signature;
	}

	protected void save(Connection connection) throws SQLException {
		String sql = DB.formatInsertWithPlaceholders("Transactions", "signature", "reference", "type", "creator", "creation", "fee", "milestone_block");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.signature, this.reference, this.type.value, this.creator.getPublicKey(),
				Timestamp.from(Instant.ofEpochSecond(this.timestamp)), this.fee, null);
		preparedStatement.execute();
	}

	// Navigation

	/**
	 * Load encapsulating Block from DB, if any
	 * 
	 * @return Block, or null if transaction is not in a Block
	 * @throws SQLException
	 */
	public Block getBlock() throws SQLException {
		if (this.signature == null)
			return null;

		BlockTransaction blockTx = BlockTransaction.fromTransactionSignature(this.signature);
		if (blockTx == null)
			return null;

		return Block.fromSignature(blockTx.getBlockSignature());
	}

	/**
	 * Load parent Transaction from DB via this transaction's reference.
	 * 
	 * @return Transaction, or null if no parent found (which should not happen)
	 * @throws SQLException
	 */
	public Transaction getParent() throws SQLException {
		if (this.reference == null)
			return null;

		return TransactionFactory.fromSignature(this.reference);
	}

	/**
	 * Load child Transaction from DB, if any.
	 * 
	 * @return Transaction, or null if no child found
	 * @throws SQLException
	 */
	public Transaction getChild() throws SQLException {
		if (this.signature == null)
			return null;

		return TransactionFactory.fromReference(this.signature);
	}

	// Converters

	public static Transaction parse(byte[] data) throws TransactionParseException {
		if (data == null)
			return null;

		if (data.length < TYPE_LENGTH)
			throw new TransactionParseException("Byte data too short to determine transaction type");

		ByteBuffer byteBuffer = ByteBuffer.wrap(data);

		TransactionType type = TransactionType.valueOf(byteBuffer.getInt());
		if (type == null)
			return null;

		switch (type) {
			case GENESIS:
				return GenesisTransaction.parse(byteBuffer);

			case PAYMENT:
				return PaymentTransaction.parse(byteBuffer);

			default:
				return null;
		}
	}

	public abstract JSONObject toJSON() throws SQLException;

	/**
	 * Produce JSON representation of common/base Transaction info.
	 * 
	 * @return JSONObject
	 * @throws SQLException
	 */
	@SuppressWarnings("unchecked")
	protected JSONObject getBaseJSON() throws SQLException {
		JSONObject json = new JSONObject();

		json.put("type", this.type.value);
		json.put("fee", this.fee.toPlainString());
		json.put("timestamp", this.timestamp);
		if (this.reference != null)
			json.put("reference", Base58.encode(this.reference));
		json.put("signature", Base58.encode(this.signature));
		json.put("confirmations", this.getConfirmations());

		return json;
	}

	/**
	 * Serialize transaction as byte[], including signature.
	 * 
	 * @return byte[]
	 */
	public abstract byte[] toBytes();

	/**
	 * Serialize transaction as byte[], stripping off trailing signature.
	 * 
	 * @return byte[]
	 */
	private byte[] toBytesLessSignature() {
		byte[] bytes = this.toBytes();
		return Arrays.copyOf(bytes, bytes.length - SIGNATURE_LENGTH);
	}

	// Processing

	public byte[] calcSignature(PrivateKeyAccount signer) {
		return signer.sign(this.toBytesLessSignature());
	}

	public boolean isSignatureValid() {
		if (this.signature == null)
			return false;

		return this.creator.verify(this.signature, this.toBytesLessSignature());
	}

	public abstract ValidationResult isValid(Connection connection);

	public abstract void process();

	public abstract void orphan();

}
