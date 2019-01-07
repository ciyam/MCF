package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelOrderTransactionData extends TransactionData {

	// Properties
	@Schema(description = "order ID to cancel", example = "2zYCM8P3PSzUxFNPAKFsSdwg9dWQcYTPCuKkuQbx3GVxTUVjXAUwEmEnvUUss11SZ3p38C16UfYb3cbXP9sRuqFx")
	private byte[] orderId;

	// Constructors

	// For JAX-RS
	protected CancelOrderTransactionData() {
		super(TransactionType.CANCEL_ASSET_ORDER);
	}

	public CancelOrderTransactionData(byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.CANCEL_ASSET_ORDER, fee, creatorPublicKey, timestamp, reference, signature);

		this.orderId = orderId;
	}

	public CancelOrderTransactionData(byte[] creatorPublicKey, byte[] orderId, BigDecimal fee, long timestamp, byte[] reference) {
		this(creatorPublicKey, orderId, fee, timestamp, reference, null);
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
