package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCreateAssetOrderTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateAssetOrderTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT have_asset_id, amount, want_asset_id, price, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM CreateAssetOrderTransactions "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			long haveAssetId = resultSet.getLong(1);
			BigDecimal amount = resultSet.getBigDecimal(2);
			long wantAssetId = resultSet.getLong(3);
			BigDecimal price = resultSet.getBigDecimal(4);
			String haveAssetName = resultSet.getString(5);
			String wantAssetName = resultSet.getString(6);

			return new CreateAssetOrderTransactionData(baseTransactionData, haveAssetId, wantAssetId, amount, price, haveAssetName, wantAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch create order transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CreateAssetOrderTransactions");

		saveHelper.bind("signature", createOrderTransactionData.getSignature()).bind("creator", createOrderTransactionData.getCreatorPublicKey())
				.bind("have_asset_id", createOrderTransactionData.getHaveAssetId()).bind("amount", createOrderTransactionData.getAmount())
				.bind("want_asset_id", createOrderTransactionData.getWantAssetId()).bind("price", createOrderTransactionData.getPrice());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save create order transaction into repository", e);
		}
	}

}
