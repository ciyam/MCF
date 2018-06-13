package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;

import data.transaction.MessageTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.crypto.Crypto;
import repository.DataException;
import repository.Repository;

public class MessageTransaction extends Transaction {

	private static final int MAX_DATA_SIZE = 4000;

	// Constructors
	public MessageTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	public ValidationResult isValid() throws DataException {
		// Lowest cost checks first

		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;

		// Are message transactions even allowed at this point?
		if (messageTransactionData.getVersion() != MessageTransaction.getVersionByTimestamp(messageTransactionData.getTimestamp()))
			return ValidationResult.NOT_YET_RELEASED;

		if (this.repository.getBlockRepository().getBlockchainHeight() < Block.MESSAGE_RELEASE_HEIGHT)
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (messageTransactionData.getData().length < 1 || messageTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check recipient is a valid address
		if (!Crypto.isValidAddress(messageTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		if (messageTransactionData.getVersion() == 1) {
			// Check amount is positive (V1)
			if (messageTransactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
				return ValidationResult.NEGATIVE_AMOUNT;
		} else {
			// Check amount is not negative (V3) as sending messages without a payment is OK
			if (messageTransactionData.getAmount().compareTo(BigDecimal.ZERO) < 0)
				return ValidationResult.NEGATIVE_AMOUNT;
		}

		// Check fee is positive
		if (messageTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		Account sender = new PublicKeyAccount(this.repository, messageTransactionData.getSenderPublicKey());
		if (!Arrays.equals(sender.getLastReference(), messageTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Does asset exist? (This test not present in gen1)
		long assetId = messageTransactionData.getAssetId();
		if (assetId != Asset.QORA && !this.repository.getAssetRepository().assetExists(assetId))
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// If asset is QORA then we need to check amount + fee in one go
		if (assetId == Asset.QORA) {
			// Check sender has enough funds for amount + fee in QORA
			if (sender.getConfirmedBalance(Asset.QORA).compareTo(messageTransactionData.getAmount().add(messageTransactionData.getFee())) == -1)
				return ValidationResult.NO_BALANCE;
		} else {
			// Check sender has enough funds for amount in whatever asset
			if (sender.getConfirmedBalance(assetId).compareTo(messageTransactionData.getAmount()) == -1)
				return ValidationResult.NO_BALANCE;

			// Check sender has enough funds for fee in QORA
			if (sender.getConfirmedBalance(Asset.QORA).compareTo(messageTransactionData.getFee()) == -1)
				return ValidationResult.NO_BALANCE;
		}

		return ValidationResult.OK;
	}

	public void process() throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;
		long assetId = messageTransactionData.getAssetId();

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Update sender's balance due to amount
		Account sender = new PublicKeyAccount(this.repository, messageTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).subtract(messageTransactionData.getAmount()));
		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).subtract(messageTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(this.repository, messageTransactionData.getRecipient());
		recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).add(messageTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(messageTransactionData.getSignature());

		// For QORA amounts only: if recipient has no reference yet, then this is their starting reference
		if (assetId == Asset.QORA && recipient.getLastReference() == null)
			recipient.setLastReference(messageTransactionData.getSignature());
	}

	public void orphan() throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) this.transactionData;
		long assetId = messageTransactionData.getAssetId();

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Update sender's balance due to amount
		Account sender = new PublicKeyAccount(this.repository, messageTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).add(messageTransactionData.getAmount()));
		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).add(messageTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(this.repository, messageTransactionData.getRecipient());
		recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).subtract(messageTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(messageTransactionData.getReference());

		/*
		 * For QORA amounts only: If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own (which
		 * would have changed their last reference) thus this is their first reference so remove it.
		 */
		if (assetId == Asset.QORA && Arrays.equals(recipient.getLastReference(), messageTransactionData.getSignature()))
			recipient.setLastReference(null);
	}

}
