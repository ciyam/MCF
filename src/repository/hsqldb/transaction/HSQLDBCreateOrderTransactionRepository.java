package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.CreateOrderTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBCreateOrderTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateOrderTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository
					.checkedExecute("SELECT have_asset_id, amount, want_asset_id, price FROM CreateAssetOrderTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			long haveAssetId = rs.getLong(1);
			BigDecimal amount = rs.getBigDecimal(2);
			long wantAssetId = rs.getLong(3);
			BigDecimal price = rs.getBigDecimal(4);

			return new CreateOrderTransactionData(creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch create order transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CreateOrderTransactionData createOrderTransactionData = (CreateOrderTransactionData) transactionData;

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
