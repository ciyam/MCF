package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupKickTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupKickTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT group_id, address, reason, member_reference, admin_reference, join_reference FROM GroupKickTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String member = resultSet.getString(2);
			String reason = resultSet.getString(3);
			byte[] memberReference = resultSet.getBytes(4);
			byte[] adminReference = resultSet.getBytes(5);
			byte[] joinReference = resultSet.getBytes(6);

			return new GroupKickTransactionData(creatorPublicKey, groupId, member, reason, memberReference, adminReference, joinReference, fee, timestamp,
					reference, signature);
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
				.bind("admin_reference", groupKickTransactionData.getAdminReference()).bind("join_reference", groupKickTransactionData.getJoinReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group kick transaction into repository", e);
		}
	}

}
