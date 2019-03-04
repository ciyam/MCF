package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;

public class HSQLDBAssetRepository implements AssetRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAssetRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// Assets

	@Override
	public AssetData fromAssetId(long assetId) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE asset_id = ?", assetId)) {
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

	@Override
	public AssetData fromAssetName(String assetName) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT owner, asset_id, description, quantity, is_divisible, reference FROM Assets WHERE asset_name = ?", assetName)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			long assetId = resultSet.getLong(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			byte[] reference = resultSet.getBytes(6);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	@Override
	public boolean assetExists(long assetId) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	@Override
	public boolean assetExists(String assetName) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_name = ?", assetName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	@Override
	public List<AssetData> getAllAssets(Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT owner, asset_id, description, quantity, is_divisible, reference, asset_name FROM Assets ORDER BY asset_name";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<AssetData> assets = new ArrayList<AssetData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return assets;

			do {
				String owner = resultSet.getString(1);
				long assetId = resultSet.getLong(2);
				String description = resultSet.getString(3);
				long quantity = resultSet.getLong(4);
				boolean isDivisible = resultSet.getBoolean(5);
				byte[] reference = resultSet.getBytes(6);
				String assetName = resultSet.getString(7);

				assets.add(new AssetData(assetId, owner, assetName, description, quantity, isDivisible, reference));
			} while (resultSet.next());

			return assets;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch all assets from repository", e);
		}
	}

	@Override
	public void save(AssetData assetData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");

		saveHelper.bind("asset_id", assetData.getAssetId()).bind("owner", assetData.getOwner()).bind("asset_name", assetData.getName())
				.bind("description", assetData.getDescription()).bind("quantity", assetData.getQuantity()).bind("is_divisible", assetData.getIsDivisible())
				.bind("reference", assetData.getReference());

		try {
			saveHelper.execute(this.repository);

			if (assetData.getAssetId() == null) {
				// Fetch new assetId
				try (ResultSet resultSet = this.repository.checkedExecute("SELECT asset_id FROM Assets WHERE reference = ?", assetData.getReference())) {
					if (resultSet == null)
						throw new DataException("Unable to fetch new asset ID from repository");

					assetData.setAssetId(resultSet.getLong(1));
				}
			}
		} catch (SQLException e) {
			throw new DataException("Unable to save asset into repository", e);
		}
	}

	@Override
	public void delete(long assetId) throws DataException {
		try {
			this.repository.delete("Assets", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset from repository", e);
		}
	}

	// Orders

	@Override
	public OrderData fromOrderId(byte[] orderId) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT creator, have_asset_id, want_asset_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled FROM AssetOrders WHERE asset_order_id = ?",
				orderId)) {
			if (resultSet == null)
				return null;

			byte[] creatorPublicKey = resultSet.getBytes(1);
			long haveAssetId = resultSet.getLong(2);
			long wantAssetId = resultSet.getLong(3);
			BigDecimal amount = resultSet.getBigDecimal(4);
			BigDecimal fulfilled = resultSet.getBigDecimal(5);
			BigDecimal price = resultSet.getBigDecimal(6);
			long timestamp = resultSet.getTimestamp(7, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			boolean isClosed = resultSet.getBoolean(8);
			boolean isFulfilled = resultSet.getBoolean(9);

			return new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed, isFulfilled);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order from repository", e);
		}
	}

	@Override
	public List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT creator, asset_order_id, amount, fulfilled, price, ordered FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND is_closed = FALSE AND is_fulfilled = FALSE ORDER BY price";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += ", ordered";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<OrderData> orders = new ArrayList<OrderData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] creatorPublicKey = resultSet.getBytes(1);
				byte[] orderId = resultSet.getBytes(2);
				BigDecimal amount = resultSet.getBigDecimal(3);
				BigDecimal fulfilled = resultSet.getBigDecimal(4);
				BigDecimal price = resultSet.getBigDecimal(5);
				long timestamp = resultSet.getTimestamp(6, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				boolean isClosed = false;
				boolean isFulfilled = false;

				OrderData order = new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed,
						isFulfilled);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAggregatedOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT price, SUM(amount - fulfilled), MAX(ordered) FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND is_closed = FALSE AND is_fulfilled = FALSE GROUP BY price ORDER BY price";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<OrderData> orders = new ArrayList<OrderData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				BigDecimal price = resultSet.getBigDecimal(1);
				BigDecimal totalUnfulfilled = resultSet.getBigDecimal(2);
				long timestamp = resultSet.getTimestamp(3).getTime();

				OrderData order = new OrderData(null, null, haveAssetId, wantAssetId, totalUnfulfilled, BigDecimal.ZERO, price, timestamp, false, false);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch aggregated open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, boolean includeClosed, boolean includeFulfilled, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT asset_order_id, have_asset_id, want_asset_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled FROM AssetOrders WHERE creator = ?";
		if (!includeClosed)
			sql += " AND is_closed = FALSE";
		if (!includeFulfilled)
			sql += " AND is_fulfilled = FALSE";
		sql += " ORDER BY ordered";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<OrderData> orders = new ArrayList<OrderData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, publicKey)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] orderId = resultSet.getBytes(1);
				long haveAssetId = resultSet.getLong(2);
				long wantAssetId = resultSet.getLong(3);
				BigDecimal amount = resultSet.getBigDecimal(4);
				BigDecimal fulfilled = resultSet.getBigDecimal(5);
				BigDecimal price = resultSet.getBigDecimal(6);
				long timestamp = resultSet.getTimestamp(7, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				boolean isClosed = resultSet.getBoolean(8);
				boolean isFulfilled = resultSet.getBoolean(9);

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed,
						isFulfilled);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, long haveAssetId, long wantAssetId, boolean includeClosed, boolean includeFulfilled, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT asset_order_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled FROM AssetOrders WHERE creator = ? AND have_asset_id = ? AND want_asset_id = ?";
		if (!includeClosed)
			sql += " AND is_closed = FALSE";
		if (!includeFulfilled)
			sql += " AND is_fulfilled = FALSE";
		sql += " ORDER BY ordered";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<OrderData> orders = new ArrayList<OrderData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, publicKey, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] orderId = resultSet.getBytes(1);
				BigDecimal amount = resultSet.getBigDecimal(2);
				BigDecimal fulfilled = resultSet.getBigDecimal(3);
				BigDecimal price = resultSet.getBigDecimal(4);
				long timestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				boolean isClosed = resultSet.getBoolean(6);
				boolean isFulfilled = resultSet.getBoolean(7);

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed,
						isFulfilled);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's asset orders from repository", e);
		}
	}

	@Override
	public void save(OrderData orderData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AssetOrders");

		saveHelper.bind("asset_order_id", orderData.getOrderId()).bind("creator", orderData.getCreatorPublicKey())
				.bind("have_asset_id", orderData.getHaveAssetId()).bind("want_asset_id", orderData.getWantAssetId()).bind("amount", orderData.getAmount())
				.bind("fulfilled", orderData.getFulfilled()).bind("price", orderData.getPrice()).bind("ordered", new Timestamp(orderData.getTimestamp()))
				.bind("is_closed", orderData.getIsClosed()).bind("is_fulfilled", orderData.getIsFulfilled());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset order into repository", e);
		}
	}

	@Override
	public void delete(byte[] orderId) throws DataException {
		try {
			this.repository.delete("AssetOrders", "asset_order_id = ?", orderId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset order from repository", e);
		}
	}

	// Trades

	@Override
	public List<TradeData> getTrades(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT initiating_order_id, target_order_id, AssetTrades.amount, AssetTrades.price, traded FROM AssetOrders JOIN AssetTrades ON initiating_order_id = asset_order_id "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? ORDER BY traded";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TradeData> trades = new ArrayList<TradeData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				BigDecimal amount = resultSet.getBigDecimal(3);
				BigDecimal price = resultSet.getBigDecimal(4);
				long timestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, amount, price, timestamp);
				trades.add(trade);
			} while (resultSet.next());

			return trades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset trades from repository", e);
		}
	}

	@Override
	public List<TradeData> getOrdersTrades(byte[] orderId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT initiating_order_id, target_order_id, amount, price, traded FROM AssetTrades WHERE initiating_order_id = ? OR target_order_id = ? ORDER BY traded";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TradeData> trades = new ArrayList<TradeData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, orderId, orderId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				BigDecimal amount = resultSet.getBigDecimal(3);
				BigDecimal price = resultSet.getBigDecimal(4);
				long timestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, amount, price, timestamp);
				trades.add(trade);
			} while (resultSet.next());

			return trades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order's trades from repository", e);
		}
	}

	@Override
	public void save(TradeData tradeData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AssetTrades");

		saveHelper.bind("initiating_order_id", tradeData.getInitiator()).bind("target_order_id", tradeData.getTarget()).bind("amount", tradeData.getAmount())
				.bind("price", tradeData.getPrice()).bind("traded", new Timestamp(tradeData.getTimestamp()));

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset trade into repository", e);
		}
	}

	@Override
	public void delete(TradeData tradeData) throws DataException {
		try {
			this.repository.delete("AssetTrades", "initiating_order_id = ? AND target_order_id = ? AND amount = ? AND price = ?", tradeData.getInitiator(),
					tradeData.getTarget(), tradeData.getAmount(), tradeData.getPrice());
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset trade from repository", e);
		}
	}

}
