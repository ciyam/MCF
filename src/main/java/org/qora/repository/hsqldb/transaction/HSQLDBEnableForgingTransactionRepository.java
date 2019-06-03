package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.EnableForgingTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBEnableForgingTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBEnableForgingTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT target FROM EnableForgingTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String target = resultSet.getString(1);

			return new EnableForgingTransactionData(baseTransactionData, target);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account flags transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		EnableForgingTransactionData enableForgingTransactionData = (EnableForgingTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("EnableForgingTransactions");

		saveHelper.bind("signature", enableForgingTransactionData.getSignature()).bind("creator", enableForgingTransactionData.getCreatorPublicKey())
				.bind("target", enableForgingTransactionData.getTarget());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account flags transaction into repository", e);
		}
	}

}
