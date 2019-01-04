package org.qora.asset;

import org.qora.data.asset.AssetData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class Asset {

	/**
	 * QORA coins are just another asset but with fixed assetId of zero.
	 */
	public static final long QORA = 0L;

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
		this.assetData = new AssetData(issueAssetTransactionData.getOwner(), issueAssetTransactionData.getAssetName(),
				issueAssetTransactionData.getDescription(), issueAssetTransactionData.getQuantity(), issueAssetTransactionData.getIsDivisible(),
				issueAssetTransactionData.getReference());
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

}
