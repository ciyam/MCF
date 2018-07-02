package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class CancelSellNameTransactionData extends TransactionData {

	// Properties
	private byte[] ownerPublicKey;
	private String name;

	// Constructors

	public CancelSellNameTransactionData(byte[] ownerPublicKey, String name, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CANCEL_SELL_NAME, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.name = name;
	}

	public CancelSellNameTransactionData(byte[] ownerPublicKey, String name, BigDecimal fee, long timestamp, byte[] reference) {
		this(ownerPublicKey, name, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getName() {
		return this.name;
	}

}
