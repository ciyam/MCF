package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class SellNameTransactionData extends TransactionData {

	// Properties
	private byte[] ownerPublicKey;
	private String name;
	private BigDecimal amount;

	// Constructors

	public SellNameTransactionData(byte[] ownerPublicKey, String name, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.SELL_NAME, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.name = name;
		this.amount = amount;
	}

	public SellNameTransactionData(byte[] ownerPublicKey, String name, BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(ownerPublicKey, name, amount, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
