package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class IssueAssetTransaction extends Transaction {

	private static final BigDecimal ISSUE_ASSET_FEE = BigDecimal.valueOf(3000L).setScale(8);

	// Properties
	private IssueAssetTransactionData issueAssetTransactionData;

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
		if (this.issueAssetTransactionData.getTimestamp() < BlockChain.getInstance().getAssetsReleaseTimestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// "data" field is only allowed in v2
		String data = this.issueAssetTransactionData.getData();
		if (this.issueAssetTransactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp()) {
			// v2 so check data field properly
			int dataLength = Utf8.encodedLength(data);
			if (data == null || dataLength < 1 || dataLength > Asset.MAX_DATA_SIZE)
				return ValidationResult.INVALID_DATA_LENGTH;
		} else {
			// pre-v2 so disallow data field
			if (data != null)
				return ValidationResult.NOT_YET_RELEASED;
		}

		// Check owner address is valid
		if (!Crypto.isValidAddress(issueAssetTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		int assetNameLength = Utf8.encodedLength(issueAssetTransactionData.getAssetName());
		if (assetNameLength < 1 || assetNameLength > Asset.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int assetDescriptionlength = Utf8.encodedLength(issueAssetTransactionData.getDescription());
		if (assetDescriptionlength < 1 || assetDescriptionlength > Asset.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check quantity - either 10 billion or if that's not enough: a billion billion!
		long maxQuantity = issueAssetTransactionData.getIsDivisible() ? Asset.MAX_DIVISIBLE_QUANTITY : Asset.MAX_INDIVISIBLE_QUANTITY;
		if (issueAssetTransactionData.getQuantity() < 1 || issueAssetTransactionData.getQuantity() > maxQuantity)
			return ValidationResult.INVALID_QUANTITY;

		// Check fee is correct amount
		if (issueAssetTransactionData.getFee().compareTo(ISSUE_ASSET_FEE) != 0)
			return ValidationResult.INSUFFICIENT_FEE;

		Account issuer = getIssuer();

		// Check issuer has enough funds
		if (issuer.getConfirmedBalance(Asset.QORA).compareTo(issueAssetTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the asset name isn't already taken. This check is not present in gen1.
		if (issueAssetTransactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			if (this.repository.getAssetRepository().assetExists(issueAssetTransactionData.getAssetName()))
				return ValidationResult.ASSET_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Issue asset
		Asset asset = new Asset(this.repository, issueAssetTransactionData);
		asset.issue();

		// Add asset to owner
		Account owner = getOwner();
		owner.setConfirmedBalance(asset.getAssetData().getAssetId(), BigDecimal.valueOf(issueAssetTransactionData.getQuantity()).setScale(8));

		// Note newly assigned asset ID in our transaction record
		issueAssetTransactionData.setAssetId(asset.getAssetData().getAssetId());

		// Save this transaction with newly assigned assetId
		this.repository.getTransactionRepository().save(issueAssetTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Remove asset from owner
		Account owner = getOwner();
		owner.deleteBalance(issueAssetTransactionData.getAssetId());

		// Deissue asset
		Asset asset = new Asset(this.repository, issueAssetTransactionData.getAssetId());
		asset.deissue();

		// Remove assigned asset ID from transaction info
		issueAssetTransactionData.setAssetId(null);

		// Save this transaction, with removed assetId
		this.repository.getTransactionRepository().save(issueAssetTransactionData);
	}

}
