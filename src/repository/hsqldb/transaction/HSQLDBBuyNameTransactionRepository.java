package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.BuyNameTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBBuyNameTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBBuyNameTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] buyerPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT name, amount, seller, name_reference FROM BuyNameTransactions WHERE signature = ?",
					signature);
			if (rs == null)
				return null;

			String name = rs.getString(1);
			BigDecimal amount = rs.getBigDecimal(2);
			String seller = rs.getString(3);
			byte[] nameReference = rs.getBytes(4);

			return new BuyNameTransactionData(buyerPublicKey, name, amount, seller, nameReference, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch buy name transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("BuyNameTransactions");

		saveHelper.bind("signature", buyNameTransactionData.getSignature()).bind("buyer", buyNameTransactionData.getBuyerPublicKey())
				.bind("name", buyNameTransactionData.getName()).bind("amount", buyNameTransactionData.getAmount())
				.bind("seller", buyNameTransactionData.getSeller()).bind("name_reference", buyNameTransactionData.getNameReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save buy name transaction into repository", e);
		}
	}

}
