package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.SellNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBSellNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBSellNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT name, amount FROM SellNameTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String name = resultSet.getString(1);
			BigDecimal amount = resultSet.getBigDecimal(2);

			return new SellNameTransactionData(timestamp, txGroupId, reference, creatorPublicKey, name, amount, fee, signature);
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
