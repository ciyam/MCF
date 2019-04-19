package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.RecentTradeData;
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
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT owner, asset_name, description, quantity, is_divisible, data, creation_group_id, reference FROM Assets WHERE asset_id = ?",
				assetId)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String assetName = resultSet.getString(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			String data = resultSet.getString(6);
			int creationGroupId = resultSet.getInt(7);
			byte[] reference = resultSet.getBytes(8);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data, creationGroupId,
					reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	@Override
	public AssetData fromAssetName(String assetName) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT owner, asset_id, description, quantity, is_divisible, data, creation_group_id, reference FROM Assets WHERE asset_name = ?",
				assetName)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			long assetId = resultSet.getLong(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			String data = resultSet.getString(6);
			int creationGroupId = resultSet.getInt(7);
			byte[] reference = resultSet.getBytes(8);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data, creationGroupId,
					reference);
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
		String sql = "SELECT asset_id, owner, asset_name, description, quantity, is_divisible, data, creation_group_id, reference FROM Assets ORDER BY asset_id";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<AssetData> assets = new ArrayList<AssetData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return assets;

			do {
				long assetId = resultSet.getLong(1);
				String owner = resultSet.getString(2);
				String assetName = resultSet.getString(3);
				String description = resultSet.getString(4);
				long quantity = resultSet.getLong(5);
				boolean isDivisible = resultSet.getBoolean(6);
				String data = resultSet.getString(7);
				int creationGroupId = resultSet.getInt(8);
				byte[] reference = resultSet.getBytes(9);

				assets.add(new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data,
						creationGroupId, reference));
			} while (resultSet.next());

			return assets;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch all assets from repository", e);
		}
	}

	@Override
	public List<Long> getRecentAssetIds(long start) throws DataException {
		String sql = "SELECT asset_id FROM IssueAssetTransactions JOIN Assets USING (asset_id) "
				+ "JOIN Transactions USING (signature) "
				+ "WHERE creation >= ?";

		List<Long> assetIds = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, HSQLDBRepository.toOffsetDateTime(start))) {
			if (resultSet == null)
				return assetIds;

			do {
				long assetId = resultSet.getLong(1);

				assetIds.add(assetId);
			} while (resultSet.next());

			return assetIds;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch recent asset IDs from repository", e);
		}
	}

	@Override
	public void save(AssetData assetData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");

		saveHelper.bind("asset_id", assetData.getAssetId()).bind("owner", assetData.getOwner())
				.bind("asset_name", assetData.getName()).bind("description", assetData.getDescription())
				.bind("quantity", assetData.getQuantity()).bind("is_divisible", assetData.getIsDivisible())
				.bind("data", assetData.getData()).bind("creation_group_id", assetData.getCreationGroupId())
				.bind("reference", assetData.getReference());

		try {
			saveHelper.execute(this.repository);

			if (assetData.getAssetId() == null) {
				// Fetch new assetId
				try (ResultSet resultSet = this.repository
						.checkedExecute("SELECT asset_id FROM Assets WHERE reference = ?", assetData.getReference())) {
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
		String sql = "SELECT creator, have_asset_id, want_asset_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM AssetOrders "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE asset_order_id = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, orderId)) {
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
			String haveAssetName = resultSet.getString(10);
			String wantAssetName = resultSet.getString(11);

			return new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed, isFulfilled, haveAssetName, wantAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order from repository", e);
		}
	}

	@Override
	public List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset,
			Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<OrderData>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		String sql = "SELECT creator, asset_order_id, amount, fulfilled, price, ordered "
				+ "FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled ";

		sql += "ORDER BY price";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += ", ordered";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

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

				OrderData order = new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetData.getName(), wantAssetData.getName());
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getOpenOrdersForTrading(long haveAssetId, long wantAssetId, BigDecimal minimumPrice) throws DataException {
		List<Object> bindParams = new ArrayList<>(3);

		String sql = "SELECT creator, asset_order_id, amount, fulfilled, price, ordered "
				+ "FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled ";

		Collections.addAll(bindParams, haveAssetId, wantAssetId);

		if (minimumPrice != null) {
			// 'new' pricing scheme implied
			// NOTE: haveAssetId and wantAssetId are for TARGET orders, so different from Order.process() caller
			if (haveAssetId < wantAssetId)
				sql += "AND price >= ? ";
			else
				sql += "AND price <= ? ";

			bindParams.add(minimumPrice);
		}

		sql += "ORDER BY price";
		if (minimumPrice == null || haveAssetId < wantAssetId)
			sql += " DESC";

		sql += ", ordered";

		List<OrderData> orders = new ArrayList<OrderData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams.toArray())) {
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

				// We don't need asset names so we can use simpler constructor
				OrderData order = new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch open asset orders for trading from repository", e);
		}
	}

	@Override
	public List<OrderData> getAggregatedOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset,
			Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<OrderData>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		String sql = "SELECT price, SUM(amount - fulfilled), MAX(ordered) "
				+ "FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled "
				+ "GROUP BY price ";

		sql += "ORDER BY price";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				BigDecimal price = resultSet.getBigDecimal(1);
				BigDecimal totalUnfulfilled = resultSet.getBigDecimal(2);
				long timestamp = resultSet.getTimestamp(3).getTime();

				OrderData order = new OrderData(null, null, haveAssetId, wantAssetId, totalUnfulfilled, BigDecimal.ZERO,
						price, timestamp, false, false, haveAssetData.getName(), wantAssetData.getName());
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch aggregated open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled,
			Integer limit, Integer offset, Boolean reverse) throws DataException {
		// We have to join for have/want asset data as it might vary
		String sql = "SELECT asset_order_id, have_asset_id, want_asset_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM AssetOrders "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE creator = ?";

		if (optIsClosed != null)
			sql += " AND is_closed = " + (optIsClosed ? "TRUE" : "FALSE");

		if (optIsFulfilled != null)
			sql += " AND is_fulfilled = " + (optIsFulfilled ? "TRUE" : "FALSE");

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
				String haveAssetName = resultSet.getString(10);
				String wantAssetName = resultSet.getString(11);

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetName, wantAssetName);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, long haveAssetId, long wantAssetId, Boolean optIsClosed,
			Boolean optIsFulfilled, Integer limit, Integer offset, Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<OrderData>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		String sql = "SELECT asset_order_id, amount, fulfilled, price, ordered, is_closed, is_fulfilled "
				+ "FROM AssetOrders "
				+ "WHERE creator = ? AND have_asset_id = ? AND want_asset_id = ?";

		if (optIsClosed != null)
			sql += " AND is_closed = " + (optIsClosed ? "TRUE" : "FALSE");

		if (optIsFulfilled != null)
			sql += " AND is_fulfilled = " + (optIsFulfilled ? "TRUE" : "FALSE");

		sql += " ORDER BY ordered";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

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

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetData.getName(), wantAssetData.getName());
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
				.bind("have_asset_id", orderData.getHaveAssetId()).bind("want_asset_id", orderData.getWantAssetId())
				.bind("amount", orderData.getAmount()).bind("fulfilled", orderData.getFulfilled())
				.bind("price", orderData.getPrice()).bind("ordered", new Timestamp(orderData.getTimestamp()))
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
	public List<TradeData> getTrades(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		List<TradeData> trades = new ArrayList<TradeData>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return trades;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return trades;

		String sql = "SELECT initiating_order_id, target_order_id, target_amount, initiator_amount, initiator_saving, traded "
			+ "FROM AssetOrders JOIN AssetTrades ON initiating_order_id = asset_order_id "
			+ "WHERE have_asset_id = ? AND want_asset_id = ? ";

		sql += "ORDER BY traded";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				BigDecimal targetAmount = resultSet.getBigDecimal(3);
				BigDecimal initiatorAmount = resultSet.getBigDecimal(4);
				BigDecimal initiatorSaving = resultSet.getBigDecimal(5);
				long timestamp = resultSet.getTimestamp(6, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, targetAmount, initiatorAmount, initiatorSaving,
						timestamp, haveAssetId, haveAssetData.getName(), wantAssetId, wantAssetData.getName());
				trades.add(trade);
			} while (resultSet.next());

			return trades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset trades from repository", e);
		}
	}

	@Override
	public List<RecentTradeData> getRecentTrades(List<Long> assetIds, List<Long> otherAssetIds, Integer limit,
			Integer offset, Boolean reverse) throws DataException {
		// Find assetID pairs that have actually been traded
		String tradedAssetsSubquery = "SELECT have_asset_id, want_asset_id "
				+ "FROM AssetTrades JOIN AssetOrders ON asset_order_id = initiating_order_id ";

		// Optionally limit traded assetID pairs
		if (!assetIds.isEmpty())
			// longs are safe enough to use literally
			tradedAssetsSubquery += "WHERE have_asset_id IN (" + String.join(", ",
					assetIds.stream().map(assetId -> assetId.toString()).collect(Collectors.toList())) + ")";

		if (!otherAssetIds.isEmpty()) {
			tradedAssetsSubquery += assetIds.isEmpty() ? " WHERE " : " AND ";
			// longs are safe enough to use literally
			tradedAssetsSubquery += "want_asset_id IN ("
					+ String.join(", ",
							otherAssetIds.stream().map(assetId -> assetId.toString()).collect(Collectors.toList()))
					+ ")";
		}

		tradedAssetsSubquery += " GROUP BY have_asset_id, want_asset_id";

		// Find recent trades using "TradedAssets" assetID pairs
		String recentTradesSubquery = "SELECT AssetTrades.target_amount, AssetTrades.initiator_amount, AssetTrades.traded "
				+ "FROM AssetOrders JOIN AssetTrades ON initiating_order_id = asset_order_id "
				+ "WHERE AssetOrders.have_asset_id = TradedAssets.have_asset_id AND AssetOrders.want_asset_id = TradedAssets.want_asset_id "
				+ "ORDER BY traded DESC LIMIT 2";

		// Put it all together
		String sql = "SELECT have_asset_id, want_asset_id, RecentTrades.target_amount, RecentTrades.initiator_amount, RecentTrades.traded "
				+ "FROM (" + tradedAssetsSubquery + ") AS TradedAssets " + ", LATERAL (" + recentTradesSubquery
				+ ") AS RecentTrades (target_amount, initiator_amount, traded) " + "ORDER BY have_asset_id";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += ", want_asset_id";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += ", RecentTrades.traded DESC ";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<RecentTradeData> recentTrades = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return recentTrades;

			do {
				long haveAssetId = resultSet.getLong(1);
				long wantAssetId = resultSet.getLong(2);
				BigDecimal otherAmount = resultSet.getBigDecimal(3);
				BigDecimal amount = resultSet.getBigDecimal(4);
				long timestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				RecentTradeData recentTrade = new RecentTradeData(haveAssetId, wantAssetId, otherAmount, amount,
						timestamp);
				recentTrades.add(recentTrade);
			} while (resultSet.next());

			return recentTrades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch recent asset trades from repository", e);
		}
	}

	@Override
	public List<TradeData> getOrdersTrades(byte[] orderId, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		String sql = "SELECT initiating_order_id, target_order_id, target_amount, initiator_amount, initiator_saving, traded, "
					+ "have_asset_id, HaveAsset.asset_name, want_asset_id, WantAsset.asset_name "
				+ "FROM AssetTrades "
				+ "JOIN AssetOrders ON asset_order_id = initiating_order_id "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE ? IN (initiating_order_id, target_order_id)";

		sql += "ORDER BY traded";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TradeData> trades = new ArrayList<TradeData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, orderId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				BigDecimal targetAmount = resultSet.getBigDecimal(3);
				BigDecimal initiatorAmount = resultSet.getBigDecimal(4);
				BigDecimal initiatorSaving = resultSet.getBigDecimal(5);
				long timestamp = resultSet.getTimestamp(6, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				long haveAssetId = resultSet.getLong(7);
				String haveAssetName = resultSet.getString(8);
				long wantAssetId = resultSet.getLong(9);
				String wantAssetName = resultSet.getString(10);

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, targetAmount, initiatorAmount, initiatorSaving, timestamp,
						haveAssetId, haveAssetName, wantAssetId, wantAssetName);
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

		saveHelper.bind("initiating_order_id", tradeData.getInitiator()).bind("target_order_id", tradeData.getTarget())
				.bind("target_amount", tradeData.getTargetAmount()).bind("initiator_amount", tradeData.getInitiatorAmount())
				.bind("initiator_saving", tradeData.getInitiatorSaving()).bind("traded", new Timestamp(tradeData.getTimestamp()));

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset trade into repository", e);
		}
	}

	@Override
	public void delete(TradeData tradeData) throws DataException {
		try {
			this.repository.delete("AssetTrades",
					"initiating_order_id = ? AND target_order_id = ? AND target_amount = ? AND initiator_amount = ?",
					tradeData.getInitiator(), tradeData.getTarget(), tradeData.getTargetAmount(),
					tradeData.getInitiatorAmount());
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset trade from repository", e);
		}
	}

}
