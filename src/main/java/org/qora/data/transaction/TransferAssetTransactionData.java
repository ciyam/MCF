package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class TransferAssetTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private String recipient;
	private BigDecimal amount;
	private long assetId;

	// Constructors

	// For JAX-RS
	protected TransferAssetTransactionData() {
	}

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
