package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.PaymentData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.payment.Payment;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class PaymentTransaction extends Transaction {

	// Properties
	private PaymentTransactionData paymentTransactionData;

	// Constructors

	public PaymentTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.paymentTransactionData = (PaymentTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, paymentTransactionData.getRecipient()));
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (address.equals(paymentTransactionData.getRecipient()))
			return true;

		return false;
	}

	@Override
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

	@Override
	public ValidationResult isValid() throws DataException {
		// Check reference is correct
		Account sender = getSender();
		if (!Arrays.equals(sender.getLastReference(), paymentTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee());
	}

	@Override
	public void process() throws DataException {
		// We would save updated transaction at this point, but it hasn't been modified

		// Wrap and delegate payment processing to Payment class. Only update recipient's last reference if transferring QORA.
		new Payment(this.repository).process(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature(), false);
	}

	@Override
	public void orphan() throws DataException {
		// Wrap and delegate payment processing to Payment class. Only revert recipient's last reference if transferring QORA.
		new Payment(this.repository).orphan(paymentTransactionData.getSenderPublicKey(), getPaymentData(), paymentTransactionData.getFee(),
				paymentTransactionData.getSignature(), paymentTransactionData.getReference(), false);

		// We would save updated transaction at this point, but it hasn't been modified
	}

}
