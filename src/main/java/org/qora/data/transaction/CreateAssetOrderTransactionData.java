package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreateAssetOrderTransactionData extends TransactionData {

	// Properties
	@Schema(description = "ID of asset on offer to give by order creator", example = "1")
	private long haveAssetId;
	@Schema(description = "ID of asset wanted to receive by order creator", example = "0")
	private long wantAssetId;
	@Schema(description = "amount of \"have\" asset to trade")
	private BigDecimal amount;
	@Schema(description = "amount of \"want\" asset to receive")
	private BigDecimal price;

	// Constructors

	// For JAXB
	protected CreateAssetOrderTransactionData() {
		super(TransactionType.CREATE_ASSET_ORDER);
	}

	public CreateAssetOrderTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, long haveAssetId, long wantAssetId,
			BigDecimal amount, BigDecimal price, BigDecimal fee, byte[] signature) {
		super(TransactionType.CREATE_ASSET_ORDER, timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.price = price;
	}

	public CreateAssetOrderTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, long haveAssetId, long wantAssetId,
			BigDecimal amount, BigDecimal price, BigDecimal fee) {
		this(timestamp, txGroupId, reference, creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, null);
	}

	// Getters/Setters

	public long getHaveAssetId() {
		return this.haveAssetId;
	}

	public long getWantAssetId() {
		return this.wantAssetId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	// Re-expose creatorPublicKey for this transaction type for JAXB
	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getOrderCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setOrderCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

}
