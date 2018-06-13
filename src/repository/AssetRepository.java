package repository;

import data.assets.AssetData;

public interface AssetRepository {

	public AssetData fromAssetId(long assetId) throws DataException;

	public boolean assetExists(long assetId) throws DataException;

	public boolean assetExists(String assetName) throws DataException;

	public void save(AssetData assetData) throws DataException;

	public void delete(long assetId) throws DataException;

}
