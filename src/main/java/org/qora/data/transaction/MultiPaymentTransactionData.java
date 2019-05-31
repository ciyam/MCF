package org.qora.data.transaction;

import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.data.PaymentData;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MultiPaymentTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private List<PaymentData> payments;

	// Constructors

	// For JAXB
	protected MultiPaymentTransactionData() {
		super(TransactionType.MULTI_PAYMENT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** From repository */
	public MultiPaymentTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, List<PaymentData> payments,
			BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(Transaction.TransactionType.MULTI_PAYMENT, timestamp, txGroupId, reference, senderPublicKey, fee, approvalStatus, height, signature);

		this.senderPublicKey = senderPublicKey;
		this.payments = payments;
	}

	/** From network/API */
	public MultiPaymentTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, senderPublicKey, payments, fee, null, null, signature);
	}

	/** New, unsigned */
	public MultiPaymentTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee) {
		this(timestamp, txGroupId, reference, senderPublicKey, payments, fee, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

}
