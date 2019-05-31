package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBRemoveGroupAdminTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBRemoveGroupAdminTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_id, admin, admin_reference FROM RemoveGroupAdminTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String admin = resultSet.getString(2);
			byte[] adminReference = resultSet.getBytes(3);

			return new RemoveGroupAdminTransactionData(timestamp, txGroupId, reference, creatorPublicKey, groupId, admin, adminReference, fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch remove group admin transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("RemoveGroupAdminTransactions");

		saveHelper.bind("signature", removeGroupAdminTransactionData.getSignature()).bind("owner", removeGroupAdminTransactionData.getOwnerPublicKey())
				.bind("group_id", removeGroupAdminTransactionData.getGroupId()).bind("admin", removeGroupAdminTransactionData.getAdmin())
				.bind("admin_reference", removeGroupAdminTransactionData.getAdminReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save remove group admin transaction into repository", e);
		}
	}

}
