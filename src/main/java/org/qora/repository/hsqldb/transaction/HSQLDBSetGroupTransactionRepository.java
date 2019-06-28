package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.SetGroupTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBSetGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBSetGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT default_group_id, previous_default_group_id FROM SetGroupTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int defaultGroupId = resultSet.getInt(1);
			Integer previousDefaultGroupId = resultSet.getInt(2);
			if (previousDefaultGroupId == 0 && resultSet.wasNull())
				previousDefaultGroupId = null;

			return new SetGroupTransactionData(baseTransactionData, defaultGroupId, previousDefaultGroupId);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch set group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		SetGroupTransactionData setGroupTransactionData = (SetGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("SetGroupTransactions");

		saveHelper.bind("signature", setGroupTransactionData.getSignature()).bind("default_group_id", setGroupTransactionData.getDefaultGroupId())
				.bind("previous_default_group_id", setGroupTransactionData.getPreviousDefaultGroupId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save set group transaction into repository", e);
		}
	}

}
