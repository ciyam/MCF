package qora.transaction;

import java.util.Arrays;

import data.PaymentData;
import data.transaction.TransactionData;
import data.transaction.TransferAssetTransactionData;
import utils.NTP;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;

public class TransferAssetTransaction extends Transaction {

	// Constructors

	public TransferAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	private PaymentData getPaymentData() {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;
		return new PaymentData(transferAssetTransactionData.getRecipient(), transferAssetTransactionData.getAssetId(),
				transferAssetTransactionData.getAmount());
	}

	@Override
	public ValidationResult isValid() throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;

		// Are IssueAssetTransactions even allowed at this point?
		if (NTP.getTime() < Block.ASSETS_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check reference is correct
		PublicKeyAccount sender = new PublicKeyAccount(this.repository, transferAssetTransactionData.getSenderPublicKey());

		if (!Arrays.equals(sender.getLastReference(), transferAssetTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Wrap asset transfer as a payment and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), transferAssetTransactionData.getFee());
	}

	// PROCESS/ORPHAN

	@Override
	public void process() throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap asset transfer as a payment and delegate processing to Payment class
		new Payment(this.repository).process(transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), transferAssetTransactionData.getFee(),
				transferAssetTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap asset transfer as a payment and delegate processing to Payment class
		new Payment(this.repository).orphan(transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), transferAssetTransactionData.getFee(),
				transferAssetTransactionData.getSignature(), transferAssetTransactionData.getReference());
	}

}
