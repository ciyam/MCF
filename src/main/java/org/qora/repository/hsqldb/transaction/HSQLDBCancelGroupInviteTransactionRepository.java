package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelGroupInviteTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_name, invitee FROM CancelGroupInviteTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);
			String invitee = resultSet.getString(2);

			return new CancelGroupInviteTransactionData(creatorPublicKey, groupName, invitee, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch cancel group invite transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CancelGroupInviteTransactionData cancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CancelGroupInviteTransactions");

		saveHelper.bind("signature", cancelGroupInviteTransactionData.getSignature()).bind("admin", cancelGroupInviteTransactionData.getAdminPublicKey())
				.bind("group_name", cancelGroupInviteTransactionData.getGroupName()).bind("invitee", cancelGroupInviteTransactionData.getInvitee());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save cancel group invite transaction into repository", e);
		}
	}

}
