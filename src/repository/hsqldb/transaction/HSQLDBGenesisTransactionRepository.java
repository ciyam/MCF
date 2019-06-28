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
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT recipient, amount, asset_id FROM GenesisTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);
			BigDecimal amount = resultSet.getBigDecimal(2).setScale(8);
			long assetId = resultSet.getLong(3);

			return new GenesisTransactionData(recipient, amount, assetId, timestamp, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch genesis transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GenesisTransactions");
		saveHelper.bind("signature", genesisTransactionData.getSignature()).bind("recipient", genesisTransactionData.getRecipient())
				.bind("amount", genesisTransactionData.getAmount()).bind("asset_id", genesisTransactionData.getAssetId());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save genesis transaction into repository", e);
		}
	}

}
