package org.qora.data.transaction;

import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.data.PaymentData;
import org.qora.transaction.Transaction;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MultiPaymentTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private List<PaymentData> payments;

	// Constructors

	// For JAX-RS
	protected MultiPaymentTransactionData() {
	}

	public MultiPaymentTransactionData(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.MULTI_PAYMENT, fee, senderPublicKey, timestamp, reference, signature);

		this.senderPublicKey = senderPublicKey;
		this.payments = payments;
	}

	public MultiPaymentTransactionData(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, long timestamp, byte[] reference) {
		this(senderPublicKey, payments, fee, timestamp, reference, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

}
