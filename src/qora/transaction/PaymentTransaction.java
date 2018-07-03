package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

	// Properties
	private PaymentTransactionData paymentTransactionData;

	// Constructors

	public PaymentTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.paymentTransactionData = (PaymentTransactionData) this.transactionData;
	}

	// More information

	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, paymentTransactionData.getRecipient()));
	}

	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (address.equals(paymentTransactionData.getRecipient()))
			return true;

		return false;
	}

	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String senderAddress = this.getSender().getAddress();

		if (address.equals(senderAddress))
			amount = amount.subtract(this.transactionData.getFee()).subtract(paymentTransactionData.getAmount());

		if (address.equals(paymentTransactionData.getRecipient()))
			amount = amount.add(paymentTransactionData.getAmount());

		return amount;
	}

	// Navigation

	public Account getSender() throws DataException {
		return new PublicKeyAccount(this.repository, this.paymentTransactionData.getSenderPublicKey());
	}

	// Processing

	private PaymentData getPaymentData() {
		return new PaymentData(paymentTransactionData.getRecipient(), Asset.QORA, paymentTransactionData.getAmount());
	}

	public ValidationResult isValid() throws DataException {
		// Check reference is correct
		Account sender = getSender();
		if (!Arrays.equals(sender.getLastReference(), paymentTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee());
	}

	public void process() throws DataException {
		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).process(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature());
	}

	public void orphan() throws DataException {
		// Delete this transaction
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).orphan(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature(), paymentTransactionData.getReference());
	}

}
