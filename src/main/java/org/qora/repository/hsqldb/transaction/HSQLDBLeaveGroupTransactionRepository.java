package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.LeaveGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBLeaveGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBLeaveGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_id, member_reference, admin_reference FROM LeaveGroupTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			byte[] memberReference = resultSet.getBytes(2);
			byte[] adminReference = resultSet.getBytes(3);

			return new LeaveGroupTransactionData(timestamp, txGroupId, reference, creatorPublicKey, groupId, memberReference, adminReference, fee, signature);
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
				.bind("admin_reference", leaveGroupTransactionData.getAdminReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save leave group transaction into repository", e);
		}
	}

}
