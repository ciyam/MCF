package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.MessageTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBMessageTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBMessageTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT version, sender, recipient, is_text, is_encrypted, amount, asset_id, data FROM MessageTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			int version = resultSet.getInt(1);
			byte[] senderPublicKey = resultSet.getBytes(2);
			String recipient = resultSet.getString(3);
			boolean isText = resultSet.getBoolean(4);
			boolean isEncrypted = resultSet.getBoolean(5);
			BigDecimal amount = resultSet.getBigDecimal(6);
			Long assetId = resultSet.getLong(7);
			byte[] data = resultSet.getBytes(8);

			return new MessageTransactionData(version, senderPublicKey, recipient, assetId, amount, data, isText, isEncrypted, fee, timestamp, reference,
					signature);
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
