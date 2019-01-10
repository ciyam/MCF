package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBUpdateGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_name, new_owner, new_description, new_is_open, group_reference FROM UpdateGroupTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);
			String newOwner = resultSet.getString(2);
			String newDescription = resultSet.getString(3);
			boolean newIsOpen = resultSet.getBoolean(4);
			byte[] groupReference = resultSet.getBytes(5);

			return new UpdateGroupTransactionData(creatorPublicKey, groupName, newOwner, newDescription, newIsOpen, groupReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch update group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateGroupTransactions");

		saveHelper.bind("signature", updateGroupTransactionData.getSignature()).bind("owner", updateGroupTransactionData.getOwnerPublicKey())
				.bind("group_name", updateGroupTransactionData.getGroupName()).bind("new_owner", updateGroupTransactionData.getNewOwner())
				.bind("new_description", updateGroupTransactionData.getNewDescription()).bind("new_is_open", updateGroupTransactionData.getNewIsOpen())
				.bind("group_reference", updateGroupTransactionData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save update group transaction into repository", e);
		}
	}

}
