package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.RegisterNameTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBRegisterNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBRegisterNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] registrantPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT owner, name, data FROM RegisterNameTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String owner = rs.getString(1);
			String name = rs.getString(2);
			String data = rs.getString(3);

			return new RegisterNameTransactionData(registrantPublicKey, owner, name, data, fee, timestamp, reference, signature);
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
