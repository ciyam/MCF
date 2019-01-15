package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupBanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupBanTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupBanTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT group_name, address, reason, time_to_live, member_reference, admin_reference FROM GroupBanTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);
			String offender = resultSet.getString(2);
			String reason = resultSet.getString(3);
			int timeToLive = resultSet.getInt(4);
			byte[] memberReference = resultSet.getBytes(5);
			byte[] adminReference = resultSet.getBytes(6);

			return new GroupBanTransactionData(creatorPublicKey, groupName, offender, reason, timeToLive, memberReference, adminReference, fee, timestamp,
					reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group ban transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupBanTransactions");

		saveHelper.bind("signature", groupBanTransactionData.getSignature()).bind("admin", groupBanTransactionData.getAdminPublicKey())
				.bind("group_name", groupBanTransactionData.getGroupName()).bind("address", groupBanTransactionData.getOffender())
				.bind("reason", groupBanTransactionData.getReason()).bind("time_to_live", groupBanTransactionData.getTimeToLive())
				.bind("member_reference", groupBanTransactionData.getMemberReference()).bind("admin_reference", groupBanTransactionData.getAdminReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group ban transaction into repository", e);
		}
	}

}
