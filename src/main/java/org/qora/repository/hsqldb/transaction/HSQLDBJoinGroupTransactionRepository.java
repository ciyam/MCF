package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBJoinGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBJoinGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_id, invite_reference, previous_group_id FROM JoinGroupTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			byte[] inviteReference = resultSet.getBytes(2);

			Integer previousGroupId = resultSet.getInt(3);
			if (resultSet.wasNull())
				previousGroupId = null;

			return new JoinGroupTransactionData(timestamp, txGroupId, reference, creatorPublicKey, groupId, inviteReference, previousGroupId, fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch join group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		JoinGroupTransactionData joinGroupTransactionData = (JoinGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("JoinGroupTransactions");

		saveHelper.bind("signature", joinGroupTransactionData.getSignature()).bind("joiner", joinGroupTransactionData.getJoinerPublicKey())
				.bind("group_id", joinGroupTransactionData.getGroupId()).bind("invite_reference", joinGroupTransactionData.getInviteReference())
				.bind("previous_group_id", joinGroupTransactionData.getPreviousGroupId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save join group transaction into repository", e);
		}
	}

}
