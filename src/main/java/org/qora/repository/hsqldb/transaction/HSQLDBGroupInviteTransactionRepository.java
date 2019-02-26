package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupInviteTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_id, invitee, time_to_live, join_reference FROM GroupInviteTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String invitee = resultSet.getString(2);
			int timeToLive = resultSet.getInt(3);
			byte[] joinReference = resultSet.getBytes(4);

			return new GroupInviteTransactionData(timestamp, txGroupId, reference, creatorPublicKey, groupId, invitee, timeToLive, joinReference, fee, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invite transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupInviteTransactions");

		saveHelper.bind("signature", groupInviteTransactionData.getSignature()).bind("admin", groupInviteTransactionData.getAdminPublicKey())
				.bind("group_id", groupInviteTransactionData.getGroupId()).bind("invitee", groupInviteTransactionData.getInvitee())
				.bind("time_to_live", groupInviteTransactionData.getTimeToLive()).bind("join_reference", groupInviteTransactionData.getJoinReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group invite transaction into repository", e);
		}
	}

}
