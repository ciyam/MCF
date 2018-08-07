package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import data.PaymentData;
import data.transaction.ArbitraryTransactionData;
import data.transaction.ArbitraryTransactionData.DataType;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBArbitraryTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBArbitraryTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT sender, version, service, data_hash from ArbitraryTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			byte[] senderPublicKey = resultSet.getBytes(1);
			int version = resultSet.getInt(2);
			int service = resultSet.getInt(3);
			byte[] dataHash = resultSet.getBytes(4);

			List<PaymentData> payments = this.getPaymentsFromSignature(signature);

			return new ArbitraryTransactionData(version, senderPublicKey, service, dataHash, DataType.DATA_HASH, payments, fee, timestamp, reference,
					signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		// Refuse to store raw data in the repository - it needs to be saved elsewhere!
		if (arbitraryTransactionData.getDataType() != DataType.DATA_HASH)
			throw new DataException("Refusing to save arbitrary transaction data into repository");

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

}
