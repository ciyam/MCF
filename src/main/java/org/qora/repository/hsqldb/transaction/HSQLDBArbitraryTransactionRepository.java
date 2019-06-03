package org.qora.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.qora.data.PaymentData;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.ArbitraryTransactionData.DataType;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBArbitraryTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBArbitraryTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT version, service, data_hash from ArbitraryTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int version = resultSet.getInt(1);
			int service = resultSet.getInt(2);
			byte[] dataHash = resultSet.getBytes(3);

			List<PaymentData> payments = this.getPaymentsFromSignature(baseTransactionData.getSignature());

			return new ArbitraryTransactionData(baseTransactionData, version, service, dataHash, DataType.DATA_HASH, payments);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		// Refuse to store raw data in the repository - it needs to be saved elsewhere!
		if (arbitraryTransactionData.getDataType() == DataType.RAW_DATA)
			this.repository.getArbitraryRepository().save(arbitraryTransactionData);

		HSQLDBSaver saveHelper = new HSQLDBSaver("ArbitraryTransactions");

		saveHelper.bind("signature", arbitraryTransactionData.getSignature()).bind("sender", arbitraryTransactionData.getSenderPublicKey())
				.bind("version", arbitraryTransactionData.getVersion()).bind("service", arbitraryTransactionData.getService())
				.bind("data_hash", arbitraryTransactionData.getData());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save arbitrary transaction into repository", e);
		}

		if (arbitraryTransactionData.getVersion() != 1)
			// Save payments. If this fails then it is the caller's responsibility to catch the DataException as the underlying transaction will have been lost.
			this.savePayments(transactionData.getSignature(), arbitraryTransactionData.getPayments());
	}

	public void delete(TransactionData transactionData) throws DataException {
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		// Potentially delete raw data stored locally too
		this.repository.getArbitraryRepository().delete(arbitraryTransactionData);
	}

}
