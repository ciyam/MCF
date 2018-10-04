package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class ATTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private String recipient;
	private BigDecimal amount;
	private byte[] message;

	// Constructors

	public ATTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, byte[] message, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.AT, fee, senderPublicKey, timestamp, reference, signature);

		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;
		this.amount = amount;
		this.message = message;
	}

	public ATTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, byte[] message, BigDecimal fee, long timestamp, byte[] reference) {
		this(senderPublicKey, recipient, amount, message, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public byte[] getMessage() {
		return this.message;
	}

}
