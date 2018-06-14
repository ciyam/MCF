package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;

import data.assets.AssetData;
import data.transaction.TransactionData;
import data.transaction.TransferAssetTransactionData;
import utils.NTP;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.crypto.Crypto;
import repository.AssetRepository;
import repository.DataException;
import repository.Repository;

public class TransferAssetTransaction extends Transaction {

	// Constructors

	public TransferAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Lowest cost checks first

		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;

		// Are IssueAssetTransactions even allowed at this point?
		if (NTP.getTime() < Block.ASSETS_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check recipient address is valid
		if (!Crypto.isValidAddress(transferAssetTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		// Check amount is positive
		if (transferAssetTransactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check fee is positive
		if (transferAssetTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		PublicKeyAccount sender = new PublicKeyAccount(this.repository, transferAssetTransactionData.getSenderPublicKey());

		if (!Arrays.equals(sender.getLastReference(), transferAssetTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check sender has enough asset balance AFTER removing fee, in case asset is QORA
		long assetId = transferAssetTransactionData.getAssetId();
		// If asset is QORA then we need to check amount + fee in one go
		if (assetId == Asset.QORA) {
			// Check sender has enough funds for amount + fee in QORA
			if (sender.getConfirmedBalance(Asset.QORA).compareTo(transferAssetTransactionData.getAmount().add(transferAssetTransactionData.getFee())) == -1)
				return ValidationResult.NO_BALANCE;
		} else {
			// Check sender has enough funds for amount in whatever asset
			if (sender.getConfirmedBalance(assetId).compareTo(transferAssetTransactionData.getAmount()) == -1)
				return ValidationResult.NO_BALANCE;

			// Check sender has enough funds for fee in QORA
			// NOTE: in Gen1 pre-POWFIX-RELEASE transactions didn't have this check
			if (transferAssetTransactionData.getTimestamp() >= Block.POWFIX_RELEASE_TIMESTAMP
					&& sender.getConfirmedBalance(Asset.QORA).compareTo(transferAssetTransactionData.getFee()) == -1)
				return ValidationResult.NO_BALANCE;
		}

		// Check asset amount is integer if asset is not divisible
		AssetRepository assetRepository = this.repository.getAssetRepository();
		AssetData assetData = assetRepository.fromAssetId(assetId);
		if (!assetData.getIsDivisible() && transferAssetTransactionData.getAmount().stripTrailingZeros().scale() > 0)
			return ValidationResult.INVALID_AMOUNT;

		return ValidationResult.OK;
	}

	// PROCESS/ORPHAN

	@Override
	public void process() throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;
		long assetId = transferAssetTransactionData.getAssetId();

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Update sender's balance due to amount
		Account sender = new PublicKeyAccount(this.repository, transferAssetTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).subtract(transferAssetTransactionData.getAmount()));
		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).subtract(transferAssetTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(this.repository, transferAssetTransactionData.getRecipient());
		recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).add(transferAssetTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(transferAssetTransactionData.getSignature());

		// For QORA amounts only: if recipient has no reference yet, then this is their starting reference
		if (assetId == Asset.QORA && recipient.getLastReference() == null)
			recipient.setLastReference(transferAssetTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;
		long assetId = transferAssetTransactionData.getAssetId();

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Update sender's balance due to amount
		Account sender = new PublicKeyAccount(this.repository, transferAssetTransactionData.getSenderPublicKey());
		sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).add(transferAssetTransactionData.getAmount()));
		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).add(transferAssetTransactionData.getFee()));

		// Update recipient's balance
		Account recipient = new Account(this.repository, transferAssetTransactionData.getRecipient());
		recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).subtract(transferAssetTransactionData.getAmount()));

		// Update sender's reference
		sender.setLastReference(transferAssetTransactionData.getReference());

		/*
		 * For QORA amounts only: If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own (which
		 * would have changed their last reference) thus this is their first reference so remove it.
		 */
		if (assetId == Asset.QORA && Arrays.equals(recipient.getLastReference(), transferAssetTransactionData.getSignature()))
			recipient.setLastReference(null);
	}

}
