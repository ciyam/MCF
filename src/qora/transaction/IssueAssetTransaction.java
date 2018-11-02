package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Utf8;

import data.transaction.IssueAssetTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.crypto.Crypto;
import repository.DataException;
import repository.Repository;

public class IssueAssetTransaction extends Transaction {

	// Properties
	private IssueAssetTransactionData issueAssetTransactionData;

	// Other useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DESCRIPTION_SIZE = 4000;

	// Constructors

	public IssueAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.issueAssetTransactionData = (IssueAssetTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getIssuer().getAddress()))
			return true;

		if (address.equals(this.getOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getIssuer().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		// NOTE: we're only interested in QORA amounts, and genesis account issued QORA so no need to check owner

		return amount;
	}

	// Navigation

	public Account getIssuer() throws DataException {
		return new PublicKeyAccount(this.repository, this.issueAssetTransactionData.getIssuerPublicKey());
	}

	public Account getOwner() throws DataException {
		return new Account(this.repository, this.issueAssetTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Are IssueAssetTransactions even allowed at this point?
		// In gen1 this used NTP.getTime() but surely the transaction's timestamp should be used
		if (this.issueAssetTransactionData.getTimestamp() < BlockChain.getAssetsReleaseTimestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// Check owner address is valid
		if (!Crypto.isValidAddress(issueAssetTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		int assetNameLength = Utf8.encodedLength(issueAssetTransactionData.getAssetName());
		if (assetNameLength < 1 || assetNameLength > IssueAssetTransaction.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int assetDescriptionlength = Utf8.encodedLength(issueAssetTransactionData.getDescription());
		if (assetDescriptionlength < 1 || assetDescriptionlength > IssueAssetTransaction.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check quantity - either 10 billion or if that's not enough: a billion billion!
		long maxQuantity = issueAssetTransactionData.getIsDivisible() ? 10_000_000_000L : 1_000_000_000_000_000_000L;
		if (issueAssetTransactionData.getQuantity() < 1 || issueAssetTransactionData.getQuantity() > maxQuantity)
			return ValidationResult.INVALID_QUANTITY;

		// Check fee is positive
		if (issueAssetTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		Account issuer = getIssuer();

		if (!Arrays.equals(issuer.getLastReference(), issueAssetTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (issuer.getConfirmedBalance(Asset.QORA).compareTo(issueAssetTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		// Check the asset name isn't already taken. This check is not present in gen1.
		if (issueAssetTransactionData.getTimestamp() >= BlockChain.getIssueAssetV2Timestamp())
			if (this.repository.getAssetRepository().assetExists(issueAssetTransactionData.getAssetName()))
				return ValidationResult.ASSET_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Issue asset
		Asset asset = new Asset(this.repository, issueAssetTransactionData);
		asset.issue();

		// Note newly assigned asset ID in our transaction record
		issueAssetTransactionData.setAssetId(asset.getAssetData().getAssetId());

		// Save this transaction, now with corresponding assetId
		this.repository.getTransactionRepository().save(issueAssetTransactionData);

		// Update issuer's balance
		Account issuer = getIssuer();
		issuer.setConfirmedBalance(Asset.QORA, issuer.getConfirmedBalance(Asset.QORA).subtract(issueAssetTransactionData.getFee()));

		// Update issuer's reference
		issuer.setLastReference(issueAssetTransactionData.getSignature());

		// Add asset to owner
		Account owner = getOwner();
		owner.setConfirmedBalance(issueAssetTransactionData.getAssetId(), BigDecimal.valueOf(issueAssetTransactionData.getQuantity()).setScale(8));
	}

	@Override
	public void orphan() throws DataException {
		// Remove asset from owner
		Account owner = getOwner();
		owner.deleteBalance(issueAssetTransactionData.getAssetId());

		// Issue asset
		Asset asset = new Asset(this.repository, issueAssetTransactionData.getAssetId());
		asset.deissue();

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(issueAssetTransactionData);

		// Update issuer's balance
		Account issuer = getIssuer();
		issuer.setConfirmedBalance(Asset.QORA, issuer.getConfirmedBalance(Asset.QORA).add(issueAssetTransactionData.getFee()));

		// Update issuer's reference
		issuer.setLastReference(issueAssetTransactionData.getReference());
	}

}
