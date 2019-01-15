package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupUnbanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupUnbanTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupUnbanTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_name, address, group_reference FROM GroupUnbanTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);
			String member = resultSet.getString(2);
			byte[] groupReference = resultSet.getBytes(3);

			return new GroupUnbanTransactionData(creatorPublicKey, groupName, member, groupReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group unban transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupUnbanTransactionData groupUnbanTransactionData = (GroupUnbanTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupUnbanTransactions");

		saveHelper.bind("signature", groupUnbanTransactionData.getSignature()).bind("admin", groupUnbanTransactionData.getAdminPublicKey())
				.bind("group_name", groupUnbanTransactionData.getGroupName()).bind("address", groupUnbanTransactionData.getMember())
				.bind("group_reference", groupUnbanTransactionData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group unban transaction into repository", e);
		}
	}

}
