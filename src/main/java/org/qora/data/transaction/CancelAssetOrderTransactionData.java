package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelAssetOrderTransactionData extends TransactionData {

	// Properties
	@Schema(description = "order ID to cancel", example = "real_order_ID_in_base58")
	private byte[] orderId;

	// Constructors

	// For JAXB
	protected CancelAssetOrderTransactionData() {
		super(TransactionType.CANCEL_ASSET_ORDER);
	}

	/** From network/API */
	public CancelAssetOrderTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(Transaction.TransactionType.CANCEL_ASSET_ORDER, timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, height, signature);

		this.orderId = orderId;
	}

	/** From network/API */
	public CancelAssetOrderTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, creatorPublicKey, orderId, fee, null, null, signature);
	}

	/** New, unsigned */
	public CancelAssetOrderTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, byte[] orderId, BigDecimal fee) {
		this(timestamp, txGroupId, reference, creatorPublicKey, orderId, fee, null);
	}

	// Getters/Setters

	public byte[] getOrderId() {
		return this.orderId;
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
