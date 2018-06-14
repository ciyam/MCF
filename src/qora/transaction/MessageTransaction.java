package qora.transaction;

import java.util.Arrays;

import data.PaymentData;
import data.transaction.MessageTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;

public class MessageTransaction extends Transaction {

	private static final int MAX_DATA_SIZE = 4000;

	// Constructors
	public MessageTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	private PaymentData getPaymentData() {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;
		return new PaymentData(messageTransactionData.getRecipient(), Asset.QORA, messageTransactionData.getAmount());
	}

	public ValidationResult isValid() throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;

		// Are message transactions even allowed at this point?
		if (messageTransactionData.getVersion() != MessageTransaction.getVersionByTimestamp(messageTransactionData.getTimestamp()))
			return ValidationResult.NOT_YET_RELEASED;

		if (this.repository.getBlockRepository().getBlockchainHeight() < Block.MESSAGE_RELEASE_HEIGHT)
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (messageTransactionData.getData().length < 1 || messageTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check reference is correct
		Account sender = new PublicKeyAccount(this.repository, messageTransactionData.getSenderPublicKey());
		if (!Arrays.equals(sender.getLastReference(), messageTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Zero-amount payments (i.e. message-only) only valid for versions later than 1
		boolean isZeroAmountValid = messageTransactionData.getVersion() > 1;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				isZeroAmountValid);
	}

	public void process() throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).process(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature());
	}

	public void orphan() throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class
		new Payment(this.repository).orphan(messageTransactionData.getSenderPublicKey(), getPaymentData(), messageTransactionData.getFee(),
				messageTransactionData.getSignature(), messageTransactionData.getReference());
	}

}
