package org.qora.repository;

import java.util.List;

import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;

public interface AssetRepository {

	// Assets

	public AssetData fromAssetId(long assetId) throws DataException;

	public AssetData fromAssetName(String assetName) throws DataException;

	public boolean assetExists(long assetId) throws DataException;

	public boolean assetExists(String assetName) throws DataException;

	public List<AssetData> getAllAssets() throws DataException;

	// For a list of asset holders, see AccountRepository.getAssetBalances

	public void save(AssetData assetData) throws DataException;

	public void delete(long assetId) throws DataException;

	// Orders

	public OrderData fromOrderId(byte[] orderId) throws DataException;

	public List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId) throws DataException;

	public void save(OrderData orderData) throws DataException;

	public void delete(byte[] orderId) throws DataException;

	// Trades

	public List<TradeData> getTrades(long haveAssetId, long wantAssetId) throws DataException;

	/** Returns TradeData for trades where orderId was involved, i.e. either initiating OR target order */
	public List<TradeData> getOrdersTrades(byte[] orderId) throws DataException;

	public void save(TradeData tradeData) throws DataException;

	public void delete(TradeData tradeData) throws DataException;

}
