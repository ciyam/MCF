package qora.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import database.DB;
import database.NoDataFoundException;
import qora.account.PublicKeyAccount;

public class PaymentTransaction extends Transaction {

	// Properties
	private PublicKeyAccount sender;
	private String recipient;
	private BigDecimal amount;

	// Property lengths
	private static final int SENDER_LENGTH = 32;
	private static final int RECIPIENT_LENGTH = 32;
	private static final int AMOUNT_LENGTH = 8;
	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	// Constructors

	public PaymentTransaction(PublicKeyAccount sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.Payment, fee, sender, timestamp, reference, signature);

		this.sender = sender;
		this.recipient = recipient;
		this.amount = amount;
	}

	public PaymentTransaction(PublicKeyAccount sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(sender, recipient, amount, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public PublicKeyAccount getSender() {
		return this.sender;
	}

	public String getRecipient() {
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
	 * @param connection
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected PaymentTransaction(Connection connection, byte[] signature) throws SQLException {
		super(connection, TransactionType.Payment, signature);

		ResultSet rs = DB.executeUsingBytes(connection, "SELECT sender, recipient, amount FROM PaymentTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.sender = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(1), CREATOR_LENGTH));
		this.recipient = rs.getString(2);
		this.amount = rs.getBigDecimal(3).setScale(8);
	}

	/**
	 * Load PaymentTransaction from DB using signature
	 * 
	 * @param connection
	 * @param signature
	 * @return PaymentTransaction, or null if not found
	 * @throws SQLException
	 */
	public static PaymentTransaction fromSignature(Connection connection, byte[] signature) throws SQLException {
		try {
			return new PaymentTransaction(connection, signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save(Connection connection) throws SQLException {
		super.save(connection);

		String sql = DB.formatInsertWithPlaceholders("PaymentTransactions", "signature", "sender", "recipient", "amount");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.signature, this.sender.getPublicKey(), this.recipient, this.amount);
		preparedStatement.execute();
	}

	// Converters

	public static Transaction parse(byte[] data) throws Exception {
		// TODO
		return null;
	}

	@Override
	public JSONObject toJSON() {
		// TODO
		return null;
	}

	public byte[] toBytes() {
		// TODO
		return new byte[0];
	}

	// Processing

	public int isValid() {
		// TODO
		return VALIDATE_OK;
	}

	public void process() {
		// TODO
	}

	public void orphan() {
		// TODO
	}

}
