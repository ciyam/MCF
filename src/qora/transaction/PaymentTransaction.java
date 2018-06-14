package qora.transaction;

import java.util.Arrays;

import data.PaymentData;
import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;

public class PaymentTransaction extends Transaction {

	// Constructors

	public PaymentTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	private PaymentData getPaymentData() {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;
		return new PaymentData(paymentTransactionData.getRecipient(), Asset.QORA, paymentTransactionData.getAmount());
	}

	public ValidationResult isValid() throws DataException {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Check reference is correct
		Account sender = new PublicKeyAccount(repository, paymentTransactionData.getSenderPublicKey());
		if (!Arrays.equals(sender.getLastReference(), paymentTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee());
	}

	public void process() throws DataException {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).process(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature());
	}

	public void orphan() throws DataException {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Delete this transaction
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).orphan(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature(), paymentTransactionData.getReference());
	}

}
