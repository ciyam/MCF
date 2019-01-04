package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelOrderTransactionData extends TransactionData {

	// Properties
	private byte[] orderId;

	// Constructors

	// For JAX-RS
	protected CancelOrderTransactionData() {
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

}
