package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupKickTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupKickTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT group_id, address, reason, member_reference, admin_reference, join_reference, previous_group_id FROM GroupKickTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String member = resultSet.getString(2);
			String reason = resultSet.getString(3);
			byte[] memberReference = resultSet.getBytes(4);
			byte[] adminReference = resultSet.getBytes(5);
			byte[] joinReference = resultSet.getBytes(6);

			Integer previousGroupId = resultSet.getInt(7);
			if (previousGroupId == 0 && resultSet.wasNull())
				previousGroupId = null;

			return new GroupKickTransactionData(baseTransactionData, groupId, member, reason, memberReference, adminReference,
					joinReference, previousGroupId);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group kick transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupKickTransactions");

		saveHelper.bind("signature", groupKickTransactionData.getSignature()).bind("admin", groupKickTransactionData.getAdminPublicKey())
				.bind("group_id", groupKickTransactionData.getGroupId()).bind("address", groupKickTransactionData.getMember())
				.bind("reason", groupKickTransactionData.getReason()).bind("member_reference", groupKickTransactionData.getMemberReference())
				.bind("admin_reference", groupKickTransactionData.getAdminReference()).bind("join_reference", groupKickTransactionData.getJoinReference())
				.bind("previous_group_id", groupKickTransactionData.getPreviousGroupId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group kick transaction into repository", e);
		}
	}

}
