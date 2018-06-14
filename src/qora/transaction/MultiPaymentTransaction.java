package qora.transaction;

import java.util.Arrays;
import java.util.List;

import data.PaymentData;
import data.transaction.MultiPaymentTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;
import utils.NTP;

public class MultiPaymentTransaction extends Transaction {

	private static final int MAX_PAYMENTS_COUNT = 400;

	// Constructors

	public MultiPaymentTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	@Override
	public ValidationResult isValid() throws DataException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) this.transactionData;
		List<PaymentData> payments = multiPaymentTransactionData.getPayments();

		// Are MultiPaymentTransactions even allowed at this point?
		if (NTP.getTime() < Block.ASSETS_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check number of payments
		if (payments.size() < 1 || payments.size() > MAX_PAYMENTS_COUNT)
			return ValidationResult.INVALID_PAYMENTS_COUNT;

		// Check reference is correct
		PublicKeyAccount sender = new PublicKeyAccount(this.repository, multiPaymentTransactionData.getSenderPublicKey());

		if (!Arrays.equals(sender.getLastReference(), multiPaymentTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check sender has enough funds for fee
		// NOTE: in Gen1 pre-POWFIX-RELEASE transactions didn't have this check
		if (multiPaymentTransactionData.getTimestamp() >= Block.POWFIX_RELEASE_TIMESTAMP
				&& sender.getConfirmedBalance(Asset.QORA).compareTo(multiPaymentTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return new Payment(this.repository).isValid(multiPaymentTransactionData.getSenderPublicKey(), payments, multiPaymentTransactionData.getFee());
	}

	// PROCESS/ORPHAN

	@Override
	public void process() throws DataException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) this.transactionData;

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).process(multiPaymentTransactionData.getSenderPublicKey(), multiPaymentTransactionData.getPayments(),
				multiPaymentTransactionData.getFee(), multiPaymentTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) this.transactionData;

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).orphan(multiPaymentTransactionData.getSenderPublicKey(), multiPaymentTransactionData.getPayments(),
				multiPaymentTransactionData.getFee(), multiPaymentTransactionData.getSignature(), multiPaymentTransactionData.getReference());
	}

}
