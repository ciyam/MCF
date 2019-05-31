package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.RegisterNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBRegisterNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBRegisterNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT owner, name, data FROM RegisterNameTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String name = resultSet.getString(2);
			String data = resultSet.getString(3);

			return new RegisterNameTransactionData(timestamp, txGroupId, reference, creatorPublicKey, owner, name, data, fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch register name transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("RegisterNameTransactions");

		saveHelper.bind("signature", registerNameTransactionData.getSignature()).bind("registrant", registerNameTransactionData.getRegistrantPublicKey())
				.bind("owner", registerNameTransactionData.getOwner()).bind("name", registerNameTransactionData.getName())
				.bind("data", registerNameTransactionData.getData());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save register name transaction into repository", e);
		}
	}

}
