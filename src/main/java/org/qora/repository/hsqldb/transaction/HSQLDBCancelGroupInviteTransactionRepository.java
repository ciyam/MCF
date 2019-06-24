package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelGroupInviteTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT group_id, invitee, invite_reference FROM CancelGroupInviteTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String invitee = resultSet.getString(2);
			byte[] inviteReference = resultSet.getBytes(3);

			return new CancelGroupInviteTransactionData(baseTransactionData, groupId, invitee, inviteReference);
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
