package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCreateAssetOrderTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateAssetOrderTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT have_asset_id, amount, want_asset_id, price FROM CreateAssetOrderTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			long haveAssetId = resultSet.getLong(1);
			BigDecimal amount = resultSet.getBigDecimal(2);
			long wantAssetId = resultSet.getLong(3);
			BigDecimal price = resultSet.getBigDecimal(4);

			return new CreateAssetOrderTransactionData(timestamp, txGroupId, reference, creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, signature);
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
