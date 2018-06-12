package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.GenesisTransactionData;
import data.transaction.TransactionData;
import database.DB;
import repository.DataException;

public class HSQLDBGenesisTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGenesisTransactionRepository(HSQLDBRepository repository) {
		super(repository);
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creator, long timestamp, BigDecimal fee) {
		try {
			ResultSet rs = DB.checkedExecute(repository.connection, "SELECT recipient, amount FROM GenesisTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String recipient = rs.getString(1);
			BigDecimal amount = rs.getBigDecimal(2).setScale(8);

			return new GenesisTransactionData(recipient, amount, timestamp, signature);
		} catch (SQLException e) {
			return null;
		}
	}

	@Override
	public void save(TransactionData transaction) throws DataException {
		super.save(transaction);

		GenesisTransactionData genesisTransaction = (GenesisTransactionData) transaction; 
		
		HSQLDBSaver saveHelper = new HSQLDBSaver("GenesisTransactions");
		saveHelper.bind("signature", genesisTransaction.getSignature()).bind("recipient", genesisTransaction.getRecipient()).bind("amount", genesisTransaction.getAmount());
		try {
			saveHelper.execute();
		} catch (SQLException e) {
			throw new DataException(e);
		}
	}

}
