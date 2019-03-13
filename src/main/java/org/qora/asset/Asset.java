package org.qora.asset;

import org.qora.data.asset.AssetData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class Asset {

	/**
	 * QORA coins are just another asset but with fixed assetId of zero.
	 */
	public static final long QORA = 0L;

	// Other useful constants

	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DESCRIPTION_SIZE = 4000;
	public static final int MAX_DATA_SIZE = 4000;

	public static final long MAX_DIVISIBLE_QUANTITY = 10_000_000_000L;
	public static final long MAX_INDIVISIBLE_QUANTITY = 1_000_000_000_000_000_000L;

	// Properties
	private Repository repository;
	private AssetData assetData;

	// Constructors

	public Asset(Repository repository, AssetData assetData) {
		this.repository = repository;
		this.assetData = assetData;
	}

	public Asset(Repository repository, IssueAssetTransactionData issueAssetTransactionData) {
		this.repository = repository;

		// NOTE: transaction's reference is used to look up newly assigned assetID on creation!
		this.assetData = new AssetData(issueAssetTransactionData.getOwner(), issueAssetTransactionData.getAssetName(),
				issueAssetTransactionData.getDescription(), issueAssetTransactionData.getQuantity(),
				issueAssetTransactionData.getIsDivisible(), issueAssetTransactionData.getData(),
				issueAssetTransactionData.getTxGroupId(), issueAssetTransactionData.getSignature());
	}

	public Asset(Repository repository, long assetId) throws DataException {
		this.repository = repository;
		this.assetData = this.repository.getAssetRepository().fromAssetId(assetId);
	}

	// Getters/setters

	public AssetData getAssetData() {
		return this.assetData;
	}

	// Processing

	public void issue() throws DataException {
		this.repository.getAssetRepository().save(this.assetData);
	}

	public void deissue() throws DataException {
		this.repository.getAssetRepository().delete(this.assetData.getAssetId());
	}

	public void update(UpdateAssetTransactionData updateAssetTransactionData) throws DataException {
		// Update reference in transaction data
		updateAssetTransactionData.setOrphanReference(this.assetData.getReference());

		// New reference is this transaction's signature
		this.assetData.setReference(updateAssetTransactionData.getSignature());

		// Update asset's owner, description and data
		this.assetData.setOwner(updateAssetTransactionData.getNewOwner());
		this.assetData.setDescription(updateAssetTransactionData.getNewDescription());
		this.assetData.setData(updateAssetTransactionData.getNewData());

		// Save updated asset
		this.repository.getAssetRepository().save(this.assetData);
	}

	public void revert(UpdateAssetTransactionData updateAssetTransactionData) throws DataException {
		// Previous asset reference is taken from this transaction's cached copy
		this.assetData.setReference(updateAssetTransactionData.getOrphanReference());

		// Previous owner, description and/or data taken from referenced transaction
		TransactionData previousTransactionData = this.repository.getTransactionRepository()
				.fromSignature(this.assetData.getReference());

		if (previousTransactionData == null)
			throw new IllegalStateException("Missing referenced transaction when orphaning UPDATE_ASSET");

		switch (previousTransactionData.getType()) {
			case ISSUE_ASSET:
				IssueAssetTransactionData previousIssueAssetTransactionData = (IssueAssetTransactionData) previousTransactionData;

				this.assetData.setOwner(previousIssueAssetTransactionData.getOwner());
				this.assetData.setDescription(previousIssueAssetTransactionData.getDescription());
				this.assetData.setData(previousIssueAssetTransactionData.getData());
				break;

			case UPDATE_ASSET:
				UpdateAssetTransactionData previousUpdateAssetTransactionData = (UpdateAssetTransactionData) previousTransactionData;

				this.assetData.setOwner(previousUpdateAssetTransactionData.getNewOwner());
				this.assetData.setDescription(previousUpdateAssetTransactionData.getNewDescription());
				this.assetData.setData(previousUpdateAssetTransactionData.getNewData());
				break;

			default:
				throw new IllegalStateException("Invalid referenced transaction when orphaning UPDATE_ASSET");
		}

		// Save reverted asset
		this.repository.getAssetRepository().save(this.assetData);
	}

}
