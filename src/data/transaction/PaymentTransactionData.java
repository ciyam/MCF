package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class PaymentTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private String recipient;
	private BigDecimal amount;

	// Constructors

	public PaymentTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.PAYMENT, fee, senderPublicKey, timestamp, reference, signature);

		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;
		this.amount = amount;
	}

	public PaymentTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(senderPublicKey, recipient, amount, fee, timestamp, reference, null);
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

}
