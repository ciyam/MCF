package data.transaction;

import java.math.BigDecimal;

import qora.assets.Asset;
import qora.transaction.Transaction.TransactionType;

public class MessageTransactionData extends TransactionData {

	// Properties
	protected int version;
	protected byte[] senderPublicKey;
	protected String recipient;
	protected Long assetId;
	protected BigDecimal amount;
	protected byte[] data;
	protected boolean isText;
	protected boolean isEncrypted;

	// Constructors
	public MessageTransactionData(int version, byte[] senderPublicKey, String recipient, Long assetId, BigDecimal amount, BigDecimal fee, byte[] data,
			boolean isText, boolean isEncrypted, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.MESSAGE, fee, senderPublicKey, timestamp, reference, signature);

		this.version = version;
		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;

		if (assetId != null)
			this.assetId = assetId;
		else
			this.assetId = Asset.QORA;

		this.amount = amount;
		this.data = data;
		this.isText = isText;
		this.isEncrypted = isEncrypted;
	}

	// Getters/Setters

	public int getVersion() {
		return this.version;
	}

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public byte[] getData() {
		return this.data;
	}

	public boolean getIsText() {
		return this.isText;
	}

	public boolean getIsEncrypted() {
		return this.isEncrypted;
	}

}
