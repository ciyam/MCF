package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;

import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import repository.DataException;
import repository.Repository;

public class PaymentTransaction extends Transaction {

	// Constructors

	public PaymentTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	public ValidationResult isValid() throws DataException {
		// Lowest cost checks first

		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Check recipient is a valid address
		if (!Crypto.isValidAddress(paymentTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		// Check amount is positive
		if (paymentTransactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check fee is positive
		if (paymentTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		Account sender = new PublicKeyAccount(repository, paymentTransactionData.getSenderPublicKey());
		if (!Arrays.equals(sender.getLastReference(), paymentTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check sender has enough funds
		if (sender.getConfirmedBalance(Asset.QORA).compareTo(paymentTransactionData.getAmount().add(paymentTransactionData.getFee())) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	public void process() throws DataException {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Update sender's balance
		Account sender = new PublicKeyAccount(repository, paymentTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(Asset.QORA,
				sender.getConfirmedBalance(Asset.QORA).subtract(paymentTransactionData.getAmount()).subtract(paymentTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(repository, paymentTransactionData.getRecipient());
		recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).add(paymentTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(paymentTransactionData.getSignature());

		// If recipient has no reference yet, then this is their starting reference
		if (recipient.getLastReference() == null)
			recipient.setLastReference(paymentTransactionData.getSignature());
	}

	public void orphan() throws DataException {
		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) this.transactionData;

		// Delete this transaction
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Update sender's balance
		Account sender = new PublicKeyAccount(repository, paymentTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(Asset.QORA,
				sender.getConfirmedBalance(Asset.QORA).add(paymentTransactionData.getAmount()).add(paymentTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(repository, paymentTransactionData.getRecipient());
		recipient.setConfirmedBalance(Asset.QORA, recipient.getConfirmedBalance(Asset.QORA).subtract(paymentTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(paymentTransactionData.getReference());

		/*
		 * If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own (which would have changed
		 * their last reference) thus this is their first reference so remove it.
		 */
		if (Arrays.equals(recipient.getLastReference(), paymentTransactionData.getSignature()))
			recipient.setLastReference(null);
	}

}
