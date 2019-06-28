package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.CancelOrderTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelOrderTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelOrderTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT asset_order_id FROM CancelAssetOrderTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			byte[] assetOrderId = resultSet.getBytes(1);

			return new CancelOrderTransactionData(creatorPublicKey, assetOrderId, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch cancel order transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CancelAssetOrderTransactions");

		saveHelper.bind("signature", cancelOrderTransactionData.getSignature()).bind("creator", cancelOrderTransactionData.getCreatorPublicKey())
				.bind("asset_order_id", cancelOrderTransactionData.getOrderId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save cancel order transaction into repository", e);
		}
	}

}
