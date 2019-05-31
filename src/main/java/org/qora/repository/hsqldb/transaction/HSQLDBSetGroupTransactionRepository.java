package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.SetGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBSetGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBSetGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT default_group_id, previous_default_group_id FROM SetGroupTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int defaultGroupId = resultSet.getInt(1);
			Integer previousDefaultGroupId = resultSet.getInt(2);
			if (resultSet.wasNull())
				previousDefaultGroupId = null;

			return new SetGroupTransactionData(timestamp, txGroupId, reference, creatorPublicKey, defaultGroupId, previousDefaultGroupId,
					fee, approvalStatus, height, signature);
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
