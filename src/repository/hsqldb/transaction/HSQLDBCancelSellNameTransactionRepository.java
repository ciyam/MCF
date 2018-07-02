package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.CancelSellNameTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelSellNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelSellNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] ownerPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT name FROM CancelSellNameTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String name = rs.getString(1);

			return new CancelSellNameTransactionData(ownerPublicKey, name, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch cancel sell name transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CancelSellNameTransactions");

		saveHelper.bind("signature", cancelSellNameTransactionData.getSignature()).bind("owner", cancelSellNameTransactionData.getOwnerPublicKey()).bind("name",
				cancelSellNameTransactionData.getName());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save cancel sell name transaction into repository", e);
		}
	}

}
