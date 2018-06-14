package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class TransferAssetTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private String recipient;
	private BigDecimal amount;
	private long assetId;

	// Constructors

	public TransferAssetTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, long assetId, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.TRANSFER_ASSET, fee, senderPublicKey, timestamp, reference, signature);

		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
	}

	public TransferAssetTransactionData(byte[] senderPublicKey, String recipient, BigDecimal amount, long assetId, BigDecimal fee, long timestamp,
			byte[] reference) {
		this(senderPublicKey, recipient, amount, assetId, fee, timestamp, reference, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public long getAssetId() {
		return this.assetId;
	}

}
