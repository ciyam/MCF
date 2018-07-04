package repository;

import data.assets.AssetData;
import data.assets.OrderData;

public interface AssetRepository {

	// Assets

	public AssetData fromAssetId(long assetId) throws DataException;

	public AssetData fromAssetName(String assetName) throws DataException;

	public boolean assetExists(long assetId) throws DataException;

	public boolean assetExists(String assetName) throws DataException;

	public void save(AssetData assetData) throws DataException;

	public void delete(long assetId) throws DataException;

	// Orders

	public OrderData fromOrderId(byte[] orderId) throws DataException;

	public void save(OrderData orderData) throws DataException;

	public void delete(byte[] orderId) throws DataException;

}
