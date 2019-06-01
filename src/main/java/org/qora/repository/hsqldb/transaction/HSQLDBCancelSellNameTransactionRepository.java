package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CancelSellNameTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelSellNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelSellNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT name FROM CancelSellNameTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String name = resultSet.getString(1);

			return new CancelSellNameTransactionData(baseTransactionData, name);
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
