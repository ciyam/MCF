package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBRemoveGroupAdminTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBRemoveGroupAdminTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_id, admin, admin_reference FROM RemoveGroupAdminTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String admin = resultSet.getString(2);
			byte[] adminReference = resultSet.getBytes(3);

			return new RemoveGroupAdminTransactionData(creatorPublicKey, groupId, admin, adminReference, fee, timestamp, reference, signature);
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
