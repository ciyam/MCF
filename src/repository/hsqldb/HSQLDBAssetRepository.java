package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.assets.AssetData;
import data.assets.OrderData;
import repository.AssetRepository;
import repository.DataException;

public class HSQLDBAssetRepository implements AssetRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAssetRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// Assets

	public AssetData fromAssetId(long assetId) throws DataException {
		try {
			ResultSet resultSet = this.repository
					.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE asset_id = ?", assetId);
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String assetName = resultSet.getString(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			byte[] reference = resultSet.getBytes(6);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	public boolean assetExists(long assetId) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	public boolean assetExists(String assetName) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_name = ?", assetName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	public void save(AssetData assetData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");
		saveHelper.bind("asset_id", assetData.getAssetId()).bind("owner", assetData.getOwner()).bind("asset_name", assetData.getName())
				.bind("description", assetData.getDescription()).bind("quantity", assetData.getQuantity()).bind("is_divisible", assetData.getIsDivisible())
				.bind("reference", assetData.getReference());

		try {
			saveHelper.execute(this.repository);

			if (assetData.getAssetId() == null)
				assetData.setAssetId(this.repository.callIdentity());
		} catch (SQLException e) {
			throw new DataException("Unable to save asset into repository", e);
		}
	}

	public void delete(long assetId) throws DataException {
		try {
			this.repository.checkedExecute("DELETE FROM Assets WHERE assetId = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset from repository", e);
		}
	}

	// Orders

	public OrderData fromOrderId(byte[] orderId) throws DataException {
		try {
			ResultSet resultSet = this.repository.checkedExecute(
					"SELECT creator, have_asset_id, want_asset_id, amount, fulfilled, price, timestamp, is_closed FROM AssetOrders WHERE asset_order_id = ?",
					orderId);
			if (resultSet == null)
				return null;

			byte[] creatorPublicKey = resultSet.getBytes(1);
			long haveAssetId = resultSet.getLong(2);
			long wantAssetId = resultSet.getLong(3);
			BigDecimal amount = resultSet.getBigDecimal(4);
			BigDecimal fulfilled = resultSet.getBigDecimal(5);
			BigDecimal price = resultSet.getBigDecimal(6);
			long timestamp = resultSet.getTimestamp(7).getTime();
			boolean isClosed = resultSet.getBoolean(8);

			return new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order from repository", e);
		}
	}

	public void save(OrderData orderData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AssetOrders");
		saveHelper.bind("asset_order_id", orderData.getOrderId()).bind("creator", orderData.getCreatorPublicKey())
				.bind("have_asset_id", orderData.getHaveAssetId()).bind("want_asset_id", orderData.getWantAssetId()).bind("amount", orderData.getAmount())
				.bind("fulfilled", orderData.getFulfilled()).bind("price", orderData.getPrice()).bind("isClosed", orderData.getIsClosed());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset order into repository", e);
		}
	}

	public void delete(byte[] orderId) throws DataException {
		try {
			this.repository.checkedExecute("DELETE FROM AssetOrders WHERE orderId = ?", orderId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset order from repository", e);
		}
	}

}
