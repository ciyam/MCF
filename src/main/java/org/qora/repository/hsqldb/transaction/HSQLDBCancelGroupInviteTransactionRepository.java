package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBCancelGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelGroupInviteTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_id, invitee, invite_reference FROM CancelGroupInviteTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String invitee = resultSet.getString(2);
			byte[] inviteReference = resultSet.getBytes(3);

			return new CancelGroupInviteTransactionData(timestamp, txGroupId, reference, creatorPublicKey, groupId, invitee, inviteReference, fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch cancel group invite transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CancelGroupInviteTransactionData cancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CancelGroupInviteTransactions");

		saveHelper.bind("signature", cancelGroupInviteTransactionData.getSignature()).bind("admin", cancelGroupInviteTransactionData.getAdminPublicKey())
				.bind("group_id", cancelGroupInviteTransactionData.getGroupId()).bind("invitee", cancelGroupInviteTransactionData.getInvitee())
				.bind("invite_reference", cancelGroupInviteTransactionData.getInviteReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save cancel group invite transaction into repository", e);
		}
	}

}
