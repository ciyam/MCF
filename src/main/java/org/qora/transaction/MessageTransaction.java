package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.PaymentData;
import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.payment.Payment;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class MessageTransaction extends Transaction {

	// Properties
	private MessageTransactionData messageTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 4000;

	// Constructors

	public MessageTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.messageTransactionData = (MessageTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, messageTransactionData.getRecipient()));
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (address.equals(messageTransactionData.getRecipient()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String senderAddress = this.getSender().getAddress();

		if (address.equals(senderAddress))
			amount = amount.subtract(this.transactionData.getFee());

		// We're only interested in QORA
		if (messageTransactionData.getAssetId() == Asset.QORA) {
			if (address.equals(messageTransactionData.getRecipient()))
				amount = amount.add(messageTransactionData.getAmount());
			else if (address.equals(senderAddress))
				amount = amount.subtract(messageTransactionData.getAmount());
		}

		return amount;
	}

	// Navigation

	public Account getSender() throws DataException {
		return new PublicKeyAccount(this.repository, this.messageTransactionData.getSenderPublicKey());
	}

	public Account getRecipient() throws DataException {
		return new Account(this.repository, this.messageTransactionData.getRecipient());
	}

	// Processing

	private PaymentData getPaymentData() {
		return new PaymentData(messageTransactionData.getRecipient(), messageTransactionData.getAssetId(), messageTransactionData.getAmount());
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Are message transactions even allowed at this point?
		if (messageTransactionData.getVersion() != MessageTransaction.getVersionByTimestamp(messageTransactionData.getTimestamp()))
			return ValidationResult.NOT_YET_RELEASED;

		if (this.repository.getBlockRepository().getBlockchainHeight() < BlockChain.getInstance().getMessageReleaseHeight())
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (messageTransactionData.getData().length < 1 || messageTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check reference is correct
		Account sender = getSender();
		if (!Arrays.equals(sender.getLastReference(), messageTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Zero-amount payments (i.e. message-only) only valid for versions later than 1
		boolean isZeroAmountValid = messageTransactionData.getVersion() > 1;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				isZeroAmountValid);
	}

	@Override
	public void process() throws DataException {
		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class. Only update recipient's last reference if transferring QORA.
		new Payment(this.repository).process(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), false);
	}

	@Override
	public void orphan() throws DataException {
		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class. Only revert recipient's last reference if transferring QORA.
		new Payment(this.repository).orphan(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), messageTransactionData.getReference(), false);
	}

}
