package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.CancelGroupBanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBCancelGroupBanTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCancelGroupBanTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_id, address, ban_reference FROM CancelGroupBanTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String member = resultSet.getString(2);
			byte[] banReference = resultSet.getBytes(3);

			return new CancelGroupBanTransactionData(creatorPublicKey, groupId, member, banReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group unban transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CancelGroupBanTransactionData groupUnbanTransactionData = (CancelGroupBanTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CancelGroupBanTransactions");

		saveHelper.bind("signature", groupUnbanTransactionData.getSignature()).bind("admin", groupUnbanTransactionData.getAdminPublicKey())
				.bind("group_id", groupUnbanTransactionData.getGroupId()).bind("address", groupUnbanTransactionData.getMember())
				.bind("ban_reference", groupUnbanTransactionData.getBanReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group unban transaction into repository", e);
		}
	}

}
