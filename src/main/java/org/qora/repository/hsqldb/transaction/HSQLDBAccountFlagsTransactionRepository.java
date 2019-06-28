package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.AccountFlagsTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBAccountFlagsTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAccountFlagsTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT target, and_mask, or_mask, xor_mask, previous_flags FROM AccountFlagsTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String target = resultSet.getString(1);
			int andMask = resultSet.getInt(2);
			int orMask = resultSet.getInt(3);
			int xorMask = resultSet.getInt(4);

			Integer previousFlags = resultSet.getInt(5);
			if (resultSet.wasNull())
				previousFlags = null;

			return new AccountFlagsTransactionData(timestamp, txGroupId, reference, creatorPublicKey, target, andMask, orMask, xorMask, previousFlags,
					fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account flags transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		AccountFlagsTransactionData accountFlagsTransactionData = (AccountFlagsTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountFlagsTransactions");

		saveHelper.bind("signature", accountFlagsTransactionData.getSignature()).bind("creator", accountFlagsTransactionData.getCreatorPublicKey())
				.bind("target", accountFlagsTransactionData.getTarget()).bind("and_mask", accountFlagsTransactionData.getAndMask())
				.bind("or_mask", accountFlagsTransactionData.getOrMask()).bind("xor_mask", accountFlagsTransactionData.getXorMask())
				.bind("previous_flags", accountFlagsTransactionData.getPreviousFlags());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account flags transaction into repository", e);
		}
	}

}
