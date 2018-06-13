package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.GenesisTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBGenesisTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGenesisTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT recipient, amount FROM GenesisTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			String recipient = rs.getString(1);
			BigDecimal amount = rs.getBigDecimal(2).setScale(8);

			return new GenesisTransactionData(recipient, amount, timestamp, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch genesis transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		super.save(transactionData);

		GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GenesisTransactions");
		saveHelper.bind("signature", genesisTransactionData.getSignature()).bind("recipient", genesisTransactionData.getRecipient()).bind("amount",
				genesisTransactionData.getAmount());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save genesis transaction into repository", e);
		}
	}

}
