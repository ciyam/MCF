package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.DeployAtTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBDeployAtTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBDeployAtTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT AT_name, description, AT_type, AT_tags, creation_bytes, amount, asset_id, AT_address FROM DeployATTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String name = resultSet.getString(1);
			String description = resultSet.getString(2);
			String atType = resultSet.getString(3);
			String tags = resultSet.getString(4);
			byte[] creationBytes = resultSet.getBytes(5);
			BigDecimal amount = resultSet.getBigDecimal(6).setScale(8);
			long assetId = resultSet.getLong(7);

			// Special null-checking for AT address
			String atAddress = resultSet.getString(8);

			return new DeployAtTransactionData(baseTransactionData, atAddress, name, description, atType, tags, creationBytes, amount, assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch deploy AT transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		DeployAtTransactionData deployATTransactionData = (DeployAtTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("DeployATTransactions");

		saveHelper.bind("signature", deployATTransactionData.getSignature()).bind("creator", deployATTransactionData.getCreatorPublicKey())
				.bind("AT_name", deployATTransactionData.getName()).bind("description", deployATTransactionData.getDescription())
				.bind("AT_type", deployATTransactionData.getAtType()).bind("AT_tags", deployATTransactionData.getTags())
				.bind("creation_bytes", deployATTransactionData.getCreationBytes()).bind("amount", deployATTransactionData.getAmount())
				.bind("asset_id", deployATTransactionData.getAssetId()).bind("AT_address", deployATTransactionData.getAtAddress());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save deploy AT transaction into repository", e);
		}
	}

}
