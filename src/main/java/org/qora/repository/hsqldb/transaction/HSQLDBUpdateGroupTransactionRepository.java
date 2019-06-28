package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBUpdateGroupTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateGroupTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT group_id, new_owner, new_description, new_is_open, new_approval_threshold, new_min_block_delay, new_max_block_delay, group_reference FROM UpdateGroupTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String newOwner = resultSet.getString(2);
			String newDescription = resultSet.getString(3);
			boolean newIsOpen = resultSet.getBoolean(4);
			ApprovalThreshold newApprovalThreshold = ApprovalThreshold.valueOf(resultSet.getInt(5));
			int newMinBlockDelay = resultSet.getInt(6);
			int newMaxBlockDelay = resultSet.getInt(7);
			byte[] groupReference = resultSet.getBytes(8);

			return new UpdateGroupTransactionData(baseTransactionData, groupId, newOwner, newDescription, newIsOpen,
					newApprovalThreshold, newMinBlockDelay, newMaxBlockDelay, groupReference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch update group transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateGroupTransactions");

		saveHelper.bind("signature", updateGroupTransactionData.getSignature()).bind("owner", updateGroupTransactionData.getOwnerPublicKey())
				.bind("group_id", updateGroupTransactionData.getGroupId()).bind("new_owner", updateGroupTransactionData.getNewOwner())
				.bind("new_description", updateGroupTransactionData.getNewDescription()).bind("new_is_open", updateGroupTransactionData.getNewIsOpen())
				.bind("new_approval_threshold", updateGroupTransactionData.getNewApprovalThreshold().value)
				.bind("new_min_block_delay", updateGroupTransactionData.getNewMinimumBlockDelay())
				.bind("new_max_block_delay", updateGroupTransactionData.getNewMaximumBlockDelay())
				.bind("group_reference", updateGroupTransactionData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save update group transaction into repository", e);
		}
	}

}
