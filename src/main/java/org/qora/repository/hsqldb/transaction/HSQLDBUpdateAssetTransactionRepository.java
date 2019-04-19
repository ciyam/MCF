package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBUpdateAssetTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateAssetTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee,
			byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT asset_id, new_owner, new_description, new_data, orphan_reference FROM UpdateAssetTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			long assetId = resultSet.getLong(1);
			String newOwner = resultSet.getString(2);
			String newDescription = resultSet.getString(3);
			String newData = resultSet.getString(4);
			byte[] orphanReference = resultSet.getBytes(5);

			return new UpdateAssetTransactionData(timestamp, txGroupId, reference, creatorPublicKey, assetId, newOwner,
					newDescription, newData, fee, orphanReference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch update asset transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		UpdateAssetTransactionData updateAssetTransactionData = (UpdateAssetTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateAssetTransactions");

		saveHelper.bind("signature", updateAssetTransactionData.getSignature())
				.bind("owner", updateAssetTransactionData.getOwnerPublicKey())
				.bind("asset_id", updateAssetTransactionData.getAssetId())
				.bind("new_owner", updateAssetTransactionData.getNewOwner())
				.bind("new_description", updateAssetTransactionData.getNewDescription())
				.bind("new_data", updateAssetTransactionData.getNewData())
				.bind("orphan_reference", updateAssetTransactionData.getOrphanReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save update asset transaction into repository", e);
		}
	}

}
