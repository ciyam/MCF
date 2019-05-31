package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBAtTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAtTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT AT_address, recipient, amount, asset_id, message FROM ATTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			String atAddress = resultSet.getString(1);
			String recipient = resultSet.getString(2);

			BigDecimal amount = resultSet.getBigDecimal(3);
			if (resultSet.wasNull())
				amount = null;

			Long assetId = resultSet.getLong(4);
			if (resultSet.wasNull())
				assetId = null;

			byte[] message = resultSet.getBytes(5);
			if (resultSet.wasNull())
				message = null;

			return new ATTransactionData(timestamp, txGroupId, reference, atAddress, recipient, amount, assetId, message, fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		ATTransactionData atTransactionData = (ATTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("ATTransactions");

		saveHelper.bind("signature", atTransactionData.getSignature()).bind("AT_address", atTransactionData.getATAddress())
				.bind("recipient", atTransactionData.getRecipient()).bind("amount", atTransactionData.getAmount())
				.bind("asset_id", atTransactionData.getAssetId()).bind("message", atTransactionData.getMessage());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT transaction into repository", e);
		}
	}

}
