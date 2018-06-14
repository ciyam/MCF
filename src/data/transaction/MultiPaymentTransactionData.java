package data.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import data.PaymentData;
import qora.transaction.Transaction;

public class MultiPaymentTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private List<PaymentData> payments;

	// Constructors

	public MultiPaymentTransactionData(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.MULTIPAYMENT, fee, senderPublicKey, timestamp, reference, signature);

		this.senderPublicKey = senderPublicKey;
		this.payments = payments;
	}

	public MultiPaymentTransactionData(byte[] senderPublicKey, BigDecimal fee, long timestamp, byte[] reference) {
		this(senderPublicKey, new ArrayList<PaymentData>(), fee, timestamp, reference, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

}
