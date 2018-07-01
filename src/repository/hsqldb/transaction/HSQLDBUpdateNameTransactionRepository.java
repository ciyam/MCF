package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.UpdateNameTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBUpdateNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] ownerPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT new_owner, name, new_data, name_reference FROM UpdateNameTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String newOwner = rs.getString(1);
			String name = rs.getString(2);
			String newData = rs.getString(3);
			byte[] nameReference = rs.getBytes(4);

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
