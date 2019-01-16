package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBAddGroupAdminTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAddGroupAdminTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_id, address FROM AddGroupAdminTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String member = resultSet.getString(2);

			return new AddGroupAdminTransactionData(creatorPublicKey, groupId, member, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch add group admin transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		AddGroupAdminTransactionData addGroupAdminTransactionData = (AddGroupAdminTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("AddGroupAdminTransactions");

		saveHelper.bind("signature", addGroupAdminTransactionData.getSignature()).bind("owner", addGroupAdminTransactionData.getOwnerPublicKey())
				.bind("group_id", addGroupAdminTransactionData.getGroupId()).bind("address", addGroupAdminTransactionData.getMember());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save add group admin transaction into repository", e);
		}
	}

}
