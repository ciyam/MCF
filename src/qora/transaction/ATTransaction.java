package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import data.PaymentData;
import data.transaction.ATTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;

public class ATTransaction extends Transaction {

	// Properties
	private ATTransactionData atTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 256;

	// Constructors

	public ATTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.atTransactionData = (ATTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, atTransactionData.getRecipient()));
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (address.equals(atTransactionData.getRecipient()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String senderAddress = this.getSender().getAddress();

		if (address.equals(senderAddress)) {
			amount = amount.subtract(this.atTransactionData.getFee());

			if (atTransactionData.getAmount() != null && atTransactionData.getAssetId() == Asset.QORA)
				amount = amount.subtract(atTransactionData.getAmount());
		}

		if (address.equals(atTransactionData.getRecipient()) && atTransactionData.getAmount() != null)
			amount = amount.add(atTransactionData.getAmount());

		return amount;
	}

	// Navigation

	public Account getSender() throws DataException {
		return new PublicKeyAccount(this.repository, this.atTransactionData.getSenderPublicKey());
	}

	// Processing

	private PaymentData getPaymentData() {
		if (atTransactionData.getAmount() == null)
			return null;

		return new PaymentData(atTransactionData.getRecipient(), atTransactionData.getAssetId(), atTransactionData.getAmount());
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Check reference is correct
		Account sender = getSender();
		if (!Arrays.equals(sender.getLastReference(), atTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		if (this.atTransactionData.getMessage().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// If we have no payment then we're done
		if (this.atTransactionData.getAmount() == null)
			return ValidationResult.OK;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(atTransactionData.getSenderPublicKey(), getPaymentData(), atTransactionData.getFee());
	}

	@Override
	public void process() throws DataException {
		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		if (this.atTransactionData.getAmount() != null)
			// Wrap and delegate payment processing to Payment class. Only update recipient's last reference if transferring QORA.
			new Payment(this.repository).process(atTransactionData.getSenderPublicKey(), getPaymentData(), atTransactionData.getFee(),
					atTransactionData.getSignature(), false);
	}

	@Override
	public void orphan() throws DataException {
		// Delete this transaction
		this.repository.getTransactionRepository().delete(this.transactionData);

		if (this.atTransactionData.getAmount() != null)
			// Wrap and delegate payment processing to Payment class. Only revert recipient's last reference if transferring QORA.
			new Payment(this.repository).orphan(atTransactionData.getSenderPublicKey(), getPaymentData(), atTransactionData.getFee(),
					atTransactionData.getSignature(), atTransactionData.getReference(), false);
	}

}
