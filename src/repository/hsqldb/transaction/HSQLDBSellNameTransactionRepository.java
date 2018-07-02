package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.SellNameTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBSellNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBSellNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] ownerPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT name, amount FROM SellNameTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String name = rs.getString(1);
			BigDecimal amount = rs.getBigDecimal(2);

			return new SellNameTransactionData(ownerPublicKey, name, amount, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch sell name transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("SellNameTransactions");

		saveHelper.bind("signature", sellNameTransactionData.getSignature()).bind("owner", sellNameTransactionData.getOwnerPublicKey())
				.bind("name", sellNameTransactionData.getName()).bind("amount", sellNameTransactionData.getAmount());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save sell name transaction into repository", e);
		}
	}

}
