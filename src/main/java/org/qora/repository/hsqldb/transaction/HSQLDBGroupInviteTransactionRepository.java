package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupInviteTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_name, invitee, time_to_live, group_reference FROM GroupInviteTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String groupName = resultSet.getString(1);
			String invitee = resultSet.getString(2);
			int timeToLive = resultSet.getInt(3);
			byte[] groupReference = resultSet.getBytes(4);

			return new GroupInviteTransactionData(creatorPublicKey, groupName, invitee, timeToLive, groupReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invite transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupInviteTransactions");

		saveHelper.bind("signature", groupInviteTransactionData.getSignature()).bind("admin", groupInviteTransactionData.getAdminPublicKey())
				.bind("group_name", groupInviteTransactionData.getGroupName()).bind("invitee", groupInviteTransactionData.getInvitee())
				.bind("time_to_live", groupInviteTransactionData.getTimeToLive()).bind("group_reference", groupInviteTransactionData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group invite transaction into repository", e);
		}
	}

	@Override
	public List<GroupInviteTransactionData> getInvitesWithGroupReference(byte[] groupReference) throws DataException {
		List<GroupInviteTransactionData> invites = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT signature FROM GroupInviteTransactions WHERE group_reference = ?", groupReference)) {
			if (resultSet == null)
				return invites;

			do {
				byte[] signature = resultSet.getBytes(1);

				invites.add((GroupInviteTransactionData) this.repository.getTransactionRepository().fromSignature(signature));
			} while (resultSet.next());

			return invites;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invite transaction from repository", e);
		}
	}

}
