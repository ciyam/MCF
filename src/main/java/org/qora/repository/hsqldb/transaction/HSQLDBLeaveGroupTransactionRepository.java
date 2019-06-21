package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.LeaveGroupTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBLeaveGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBLeaveGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT group_id, member_reference, admin_reference, previous_group_id FROM LeaveGroupTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			byte[] memberReference = resultSet.getBytes(2);
			byte[] adminReference = resultSet.getBytes(3);

			Integer previousGroupId = resultSet.getInt(4);
			if (previousGroupId == 0 && resultSet.wasNull())
				previousGroupId = null;

			return new LeaveGroupTransactionData(baseTransactionData, groupId, memberReference, adminReference, previousGroupId);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch leave group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		LeaveGroupTransactionData leaveGroupTransactionData = (LeaveGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("LeaveGroupTransactions");

		saveHelper.bind("signature", leaveGroupTransactionData.getSignature()).bind("leaver", leaveGroupTransactionData.getLeaverPublicKey())
				.bind("group_id", leaveGroupTransactionData.getGroupId()).bind("member_reference", leaveGroupTransactionData.getMemberReference())
				.bind("admin_reference", leaveGroupTransactionData.getAdminReference()).bind("previous_group_id", leaveGroupTransactionData.getPreviousGroupId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save leave group transaction into repository", e);
		}
	}

}
