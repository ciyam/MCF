package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCreateGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT owner, group_name, description, is_open, approval_threshold, min_block_delay, max_block_delay, group_id FROM CreateGroupTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String groupName = resultSet.getString(2);
			String description = resultSet.getString(3);
			boolean isOpen = resultSet.getBoolean(4);

			ApprovalThreshold approvalThreshold = ApprovalThreshold.valueOf(resultSet.getInt(5));

			int minBlockDelay = resultSet.getInt(6);
			int maxBlockDelay = resultSet.getInt(7);

			Integer groupId = resultSet.getInt(8);
			if (groupId == 0 && resultSet.wasNull())
				groupId = null;

			return new CreateGroupTransactionData(baseTransactionData, owner, groupName, description, isOpen, approvalThreshold,
					minBlockDelay, maxBlockDelay, groupId);
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
				.bind("description", createGroupTransactionData.getDescription()).bind("is_open", createGroupTransactionData.getIsOpen())
				.bind("approval_threshold", createGroupTransactionData.getApprovalThreshold().value)
				.bind("min_block_delay", createGroupTransactionData.getMinimumBlockDelay())
				.bind("max_block_delay", createGroupTransactionData.getMaximumBlockDelay()).bind("group_id", createGroupTransactionData.getGroupId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save create group transaction into repository", e);
		}
	}

}
