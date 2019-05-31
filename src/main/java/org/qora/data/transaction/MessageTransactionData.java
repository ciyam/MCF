package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.asset.Asset;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MessageTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private int version;
	private String recipient;
	private Long assetId;
	private BigDecimal amount;
	private byte[] data;
	private boolean isText;
	private boolean isEncrypted;

	// Constructors

	// For JAXB
	protected MessageTransactionData() {
		super(TransactionType.MESSAGE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** From repository */
	public MessageTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, String recipient, Long assetId,
			BigDecimal amount, byte[] data, boolean isText, boolean isEncrypted, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.MESSAGE, timestamp, txGroupId, reference, senderPublicKey, fee, approvalStatus, height, signature);

		this.senderPublicKey = senderPublicKey;
		this.version = version;
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

	/** From network/API */
	public MessageTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, String recipient, Long assetId,
			BigDecimal amount, byte[] data, boolean isText, boolean isEncrypted, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, recipient, assetId, amount, data, isText, isEncrypted, fee, null, null, signature);
	}

	/** New, unsigned */
	public MessageTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, String recipient, Long assetId,
			BigDecimal amount, byte[] data, boolean isText, boolean isEncrypted, BigDecimal fee) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, recipient, assetId, amount, data, isText, isEncrypted, fee, null);
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getVersion() {
		return this.version;
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
