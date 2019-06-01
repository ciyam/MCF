package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.asset.Asset;
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

	public MessageTransactionData(BaseTransactionData baseTransactionData,
			int version, String recipient, Long assetId, BigDecimal amount, byte[] data, boolean isText, boolean isEncrypted) {
		super(TransactionType.MESSAGE, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
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
