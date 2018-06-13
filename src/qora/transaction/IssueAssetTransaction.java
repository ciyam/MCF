package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;

import data.assets.AssetData;
import data.transaction.IssueAssetTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.crypto.Crypto;
import repository.DataException;
import repository.Repository;
import transform.transaction.IssueAssetTransactionTransformer;
import utils.NTP;

public class IssueAssetTransaction extends Transaction {

	// Constructors

	public IssueAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	public ValidationResult isValid() throws DataException {
		// Lowest cost checks first

		IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) this.transactionData;

		// Are IssueAssetTransactions even allowed at this point?
		if (NTP.getTime() < Block.ASSETS_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check owner address is valid
		if (!Crypto.isValidAddress(issueAssetTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		if (issueAssetTransactionData.getAssetName().length() < 1
				|| issueAssetTransactionData.getAssetName().length() > IssueAssetTransactionTransformer.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		if (issueAssetTransactionData.getDescription().length() < 1
				|| issueAssetTransactionData.getDescription().length() > IssueAssetTransactionTransformer.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check quantity - either 10 billion or if that's not enough: a billion billion!
		long maxQuantity = issueAssetTransactionData.getIsDivisible() ? 10_000_000_000L : 1_000_000_000_000_000_000L;
		if (issueAssetTransactionData.getQuantity() < 1 || issueAssetTransactionData.getQuantity() > maxQuantity)
			return ValidationResult.INVALID_QUANTITY;

		// Check fee is positive
		if (issueAssetTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		PublicKeyAccount issuer = new PublicKeyAccount(this.repository, issueAssetTransactionData.getIssuerPublicKey());

		if (!Arrays.equals(issuer.getLastReference(), issueAssetTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (issuer.getConfirmedBalance(Asset.QORA).compareTo(issueAssetTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		// XXX: Surely we want to check the asset name isn't already taken? This check is not present in gen1.
		if (this.repository.getAssetRepository().assetExists(issueAssetTransactionData.getAssetName()))
			return ValidationResult.ASSET_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	public void process() throws DataException {
		IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) this.transactionData;

		// Issue asset
		AssetData assetData = new AssetData(issueAssetTransactionData.getOwner(), issueAssetTransactionData.getAssetName(),
				issueAssetTransactionData.getDescription(), issueAssetTransactionData.getQuantity(), issueAssetTransactionData.getIsDivisible(),
				issueAssetTransactionData.getReference());
		this.repository.getAssetRepository().save(assetData);

		// Note newly assigned asset ID in our transaction record
		issueAssetTransactionData.setAssetId(assetData.getAssetId());

		// Save this transaction, now with corresponding assetId
		this.repository.getTransactionRepository().save(issueAssetTransactionData);

		// Update issuer's balance
		Account issuer = new PublicKeyAccount(this.repository, issueAssetTransactionData.getIssuerPublicKey());
		issuer.setConfirmedBalance(Asset.QORA, issuer.getConfirmedBalance(Asset.QORA).subtract(issueAssetTransactionData.getFee()));

		// Update issuer's reference
		issuer.setLastReference(issueAssetTransactionData.getSignature());

		// Add asset to owner
		Account owner = new Account(this.repository, issueAssetTransactionData.getOwner());
		owner.setConfirmedBalance(issueAssetTransactionData.getAssetId(), BigDecimal.valueOf(issueAssetTransactionData.getQuantity()).setScale(8));
	}

	public void orphan() throws DataException {
		IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) this.transactionData;

		// Remove asset from owner
		Account owner = new Account(this.repository, issueAssetTransactionData.getOwner());
		owner.deleteBalance(issueAssetTransactionData.getAssetId());

		// Unissue asset
		this.repository.getAssetRepository().delete(issueAssetTransactionData.getAssetId());

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(issueAssetTransactionData);

		// Update issuer's balance
		Account issuer = new PublicKeyAccount(this.repository, issueAssetTransactionData.getIssuerPublicKey());
		issuer.setConfirmedBalance(Asset.QORA, issuer.getConfirmedBalance(Asset.QORA).add(issueAssetTransactionData.getFee()));

		// Update issuer's reference
		issuer.setLastReference(issueAssetTransactionData.getReference());
	}

}
