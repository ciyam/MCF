package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCreateGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT owner, group_name, description, is_open FROM CreateGroupTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String groupName = resultSet.getString(2);
			String description = resultSet.getString(3);
			boolean isOpen = resultSet.getBoolean(4);

			return new CreateGroupTransactionData(creatorPublicKey, owner, groupName, description, isOpen, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch create group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CreateGroupTransactions");

		saveHelper.bind("signature", createGroupTransactionData.getSignature()).bind("creator", createGroupTransactionData.getCreatorPublicKey())
				.bind("owner", createGroupTransactionData.getOwner()).bind("group_name", createGroupTransactionData.getGroupName())
				.bind("description", createGroupTransactionData.getDescription()).bind("is_open", createGroupTransactionData.getIsOpen());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save create group transaction into repository", e);
		}
	}

}
