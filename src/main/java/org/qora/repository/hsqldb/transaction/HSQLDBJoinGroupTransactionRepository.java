package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBJoinGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBJoinGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_name FROM JoinGroupTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);

			return new JoinGroupTransactionData(creatorPublicKey, groupName, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch join group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		JoinGroupTransactionData joinGroupTransactionData = (JoinGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("JoinGroupTransactions");

		saveHelper.bind("signature", joinGroupTransactionData.getSignature()).bind("joiner", joinGroupTransactionData.getJoinerPublicKey())
				.bind("group_name", joinGroupTransactionData.getGroupName());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save join group transaction into repository", e);
		}
	}

}
