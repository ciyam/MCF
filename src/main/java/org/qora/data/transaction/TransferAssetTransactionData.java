package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class TransferAssetTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private String recipient;
	private BigDecimal amount;
	private long assetId;

	// Constructors

	// For JAXB
	protected TransferAssetTransactionData() {
		super(TransactionType.TRANSFER_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public TransferAssetTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, String recipient, BigDecimal amount,
			long assetId, BigDecimal fee, byte[] signature) {
		super(TransactionType.TRANSFER_ASSET, timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
	}

	public TransferAssetTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, String recipient, BigDecimal amount,
			long assetId, BigDecimal fee) {
		this(timestamp, txGroupId, reference, senderPublicKey, recipient, amount, assetId, fee, null);
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
