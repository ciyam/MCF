package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction;

public class CancelOrderTransactionData extends TransactionData {

	// Properties
	private byte[] creatorPublicKey;
	private byte[] orderId;

	// Constructors

	public CancelOrderTransactionData(byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.CANCEL_ASSET_ORDER, fee, creatorPublicKey, timestamp, reference, signature);

		this.creatorPublicKey = creatorPublicKey;
		this.orderId = orderId;
	}

	public CancelOrderTransactionData(byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, long timestamp, byte[] reference) {
		this(creatorPublicKey, orderId, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	public byte[] getOrderId() {
		return this.orderId;
	}

}
