package qora.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DB;
import database.NoDataFoundException;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import utils.Base58;
import utils.Serialization;

public class PaymentTransaction extends Transaction {

	// Properties
	private PublicKeyAccount sender;
	private Account recipient;
	private BigDecimal amount;

	// Property lengths
	private static final int SENDER_LENGTH = 32;
	private static final int AMOUNT_LENGTH = 8;
	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	// Constructors

	public PaymentTransaction(PublicKeyAccount sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.PAYMENT, fee, sender, timestamp, reference, signature);

		this.sender = sender;
		this.recipient = new Account(recipient);
		this.amount = amount;
	}

	public PaymentTransaction(PublicKeyAccount sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(sender, recipient, amount, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public PublicKeyAccount getSender() {
		return this.sender;
	}

	public Account getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	// More information

	public int getDataLength() {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	// Load/Save

	/**
	 * Load PaymentTransaction from DB using signature.
	 * 
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected PaymentTransaction(byte[] signature) throws SQLException {
		super(TransactionType.PAYMENT, signature);

		ResultSet rs = DB.executeUsingBytes("SELECT sender, recipient, amount FROM PaymentTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.sender = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(1), CREATOR_LENGTH));
		this.recipient = new Account(rs.getString(2));
		this.amount = rs.getBigDecimal(3).setScale(8);
	}

	/**
	 * Load PaymentTransaction from DB using signature
	 * 
	 * @param signature
	 * @return PaymentTransaction, or null if not found
	 * @throws SQLException
	 */
	public static PaymentTransaction fromSignature(byte[] signature) throws SQLException {
		try {
			return new PaymentTransaction(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save(Connection connection) throws SQLException {
		super.save(connection);

		String sql = DB.formatInsertWithPlaceholders("PaymentTransactions", "signature", "sender", "recipient", "amount");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.signature, this.sender.getPublicKey(), this.recipient.getAddress(), this.amount);
		preparedStatement.execute();
	}

	// Converters

	protected static Transaction parse(ByteBuffer byteBuffer) throws TransactionParseException {
		// TODO
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransactionParseException("Byte data too short for PaymentTransaction");

		long timestamp = byteBuffer.getLong();
		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);
		PublicKeyAccount sender = Serialization.deserializePublicKey(byteBuffer);
		String recipient = Serialization.deserializeRecipient(byteBuffer);
		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);
		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);
		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new PaymentTransaction(sender, recipient, amount, fee, timestamp, reference, signature);
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJSON() throws SQLException {
		JSONObject json = getBaseJSON();

		json.put("sender", this.sender.getAddress());
		json.put("senderPublicKey", HashCode.fromBytes(this.sender.getPublicKey()).toString());
		json.put("recipient", this.recipient.getAddress());
		json.put("amount", this.amount.toPlainString());

		return json;
	}

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.type.value));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(this.reference);
			bytes.write(this.sender.getPublicKey());
			bytes.write(Base58.decode(this.recipient.getAddress()));
			bytes.write(Serialization.serializeBigDecimal(this.amount));
			bytes.write(Serialization.serializeBigDecimal(this.fee));
			bytes.write(this.signature);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Processing

	public ValidationResult isValid(Connection connection) {
		// TODO
		return ValidationResult.OK;
	}

	public void process(Connection connection) {
		// TODO
	}

	public void orphan(Connection connection) {
		// TODO
	}

}
