package qora.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import database.DB;
import database.NoDataFoundException;

public class PaymentTransaction extends Transaction {

	// Properties
	// private PublicKeyAccount sender;
	private String sender;
	private String recipient;
	private BigDecimal amount;

	// Property lengths
	private static final int SENDER_LENGTH = 32;
	private static final int RECIPIENT_LENGTH = 32;
	private static final int AMOUNT_LENGTH = 8;
	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	// Constructors

	public PaymentTransaction(String sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.Payment, fee, sender, timestamp, reference, signature);

		this.sender = sender;
		this.recipient = recipient;
		this.amount = amount;
	}

	public PaymentTransaction(String sender, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(sender, recipient, amount, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public String getSender() {
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

	public PaymentTransaction(Connection connection, byte[] signature) throws SQLException {
		super(connection, TransactionType.Payment, signature);

		ResultSet rs = DB.executeUsingBytes(connection, "SELECT sender, recipient, amount FROM PaymentTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.sender = rs.getString(1);
		this.recipient = rs.getString(2);
		this.amount = rs.getBigDecimal(3).setScale(8);
	}

	@Override
	public void save(Connection connection) throws SQLException {
		super.save(connection);

		String sql = DB.formatInsertWithPlaceholders("PaymentTransactions", "signature", "sender", "recipient", "amount");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.signature, this.sender, this.recipient, this.amount);
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
	}

	public void orphan() {
	}

}
