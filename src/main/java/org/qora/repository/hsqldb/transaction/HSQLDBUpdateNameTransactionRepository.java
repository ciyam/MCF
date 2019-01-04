package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBUpdateNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] ownerPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT new_owner, name, new_data, name_reference FROM UpdateNameTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String newOwner = resultSet.getString(1);
			String name = resultSet.getString(2);
			String newData = resultSet.getString(3);
			byte[] nameReference = resultSet.getBytes(4);

			return new UpdateNameTransactionData(ownerPublicKey, newOwner, name, newData, nameReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch update name transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateNameTransactions");

		saveHelper.bind("signature", updateNameTransactionData.getSignature()).bind("owner", updateNameTransactionData.getOwnerPublicKey())
				.bind("new_owner", updateNameTransactionData.getNewOwner()).bind("name", updateNameTransactionData.getName())
				.bind("new_data", updateNameTransactionData.getNewData()).bind("name_reference", updateNameTransactionData.getNameReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save update name transaction into repository", e);
		}
	}

}
