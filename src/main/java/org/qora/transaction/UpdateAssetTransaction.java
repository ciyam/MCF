package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.data.asset.AssetData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class UpdateAssetTransaction extends Transaction {

	// Properties
	private UpdateAssetTransactionData updateAssetTransactionData;

	// Constructors

	public UpdateAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.updateAssetTransactionData = (UpdateAssetTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getNewOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getOwner().getAddress()))
			return true;

		if (address.equals(this.getNewOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getOwner().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public PublicKeyAccount getOwner() throws DataException {
		return new PublicKeyAccount(this.repository, this.updateAssetTransactionData.getOwnerPublicKey());
	}

	public Account getNewOwner() throws DataException {
		return new Account(this.repository, this.updateAssetTransactionData.getNewOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// V2-only transaction
		if (this.updateAssetTransactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// Check asset actually exists
		AssetData assetData = this.repository.getAssetRepository().fromAssetId(updateAssetTransactionData.getAssetId());
		if (assetData == null)
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// Check transaction's public key matches asset's current owner
		PublicKeyAccount currentOwner = getOwner();
		if (!assetData.getOwner().equals(currentOwner.getAddress()))
			return ValidationResult.INVALID_ASSET_OWNER;

		// Check new owner address is valid
		if (!Crypto.isValidAddress(updateAssetTransactionData.getNewOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check new description size bounds. Note: zero length means DO NOT CHANGE description
		int newDescriptionLength = Utf8.encodedLength(updateAssetTransactionData.getNewDescription());
		if (newDescriptionLength > Asset.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check new data size bounds. Note: zero length means DO NOT CHANGE data
		int newDataLength = Utf8.encodedLength(updateAssetTransactionData.getNewData());
		if (newDataLength > Asset.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// As this transaction type could require approval, check txGroupId
		// matches groupID at creation
		if (assetData.getCreationGroupId() != updateAssetTransactionData.getTxGroupId())
			return ValidationResult.TX_GROUP_ID_MISMATCH;

		// Check fee is positive
		if (updateAssetTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(currentOwner.getLastReference(), updateAssetTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check current owner has enough funds
		if (currentOwner.getConfirmedBalance(Asset.QORA).compareTo(updateAssetTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Asset
		Asset asset = new Asset(this.repository, updateAssetTransactionData.getAssetId());
		asset.update(updateAssetTransactionData);

		// Save this transaction, with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(updateAssetTransactionData);

		// Update old owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA,
				owner.getConfirmedBalance(Asset.QORA).subtract(updateAssetTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(updateAssetTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert asset
		Asset asset = new Asset(this.repository, updateAssetTransactionData.getAssetId());
		asset.revert(updateAssetTransactionData);

		// Save this transaction, with removed "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(updateAssetTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA,
				owner.getConfirmedBalance(Asset.QORA).add(updateAssetTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(updateAssetTransactionData.getReference());
	}

}
