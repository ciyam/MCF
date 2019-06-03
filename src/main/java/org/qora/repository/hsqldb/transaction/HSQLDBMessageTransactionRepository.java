package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBMessageTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBMessageTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT version, recipient, is_text, is_encrypted, amount, asset_id, data FROM MessageTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int version = resultSet.getInt(1);
			String recipient = resultSet.getString(2);
			boolean isText = resultSet.getBoolean(3);
			boolean isEncrypted = resultSet.getBoolean(4);
			BigDecimal amount = resultSet.getBigDecimal(5);

			// Special null-checking for asset ID
			Long assetId = resultSet.getLong(6);
			if (assetId == 0 && resultSet.wasNull())
				assetId = null;

			byte[] data = resultSet.getBytes(7);

			return new MessageTransactionData(baseTransactionData, version, recipient, assetId, amount, data, isText, isEncrypted);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch message transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("MessageTransactions");

		saveHelper.bind("signature", messageTransactionData.getSignature()).bind("version", messageTransactionData.getVersion())
				.bind("sender", messageTransactionData.getSenderPublicKey()).bind("recipient", messageTransactionData.getRecipient())
				.bind("is_text", messageTransactionData.getIsText()).bind("is_encrypted", messageTransactionData.getIsEncrypted())
				.bind("amount", messageTransactionData.getAmount()).bind("asset_id", messageTransactionData.getAssetId())
				.bind("data", messageTransactionData.getData());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save message transaction into repository", e);
		}
	}

}
