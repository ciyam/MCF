package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBTransferAssetTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBTransferAssetTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT recipient, asset_id, amount FROM TransferAssetTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);
			long assetId = resultSet.getLong(2);
			BigDecimal amount = resultSet.getBigDecimal(3);

			return new TransferAssetTransactionData(timestamp, txGroupId, reference, creatorPublicKey, recipient, amount, assetId, fee, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transfer asset transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("TransferAssetTransactions");

		saveHelper.bind("signature", transferAssetTransactionData.getSignature()).bind("sender", transferAssetTransactionData.getSenderPublicKey())
				.bind("recipient", transferAssetTransactionData.getRecipient()).bind("asset_id", transferAssetTransactionData.getAssetId())
				.bind("amount", transferAssetTransactionData.getAmount());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save transfer asset transaction into repository", e);
		}
	}

}
