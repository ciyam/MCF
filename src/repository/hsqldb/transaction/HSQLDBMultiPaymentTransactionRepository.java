package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import data.PaymentData;
import data.transaction.MultiPaymentTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBMultiPaymentTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBMultiPaymentTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT sender MultiPaymentTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			byte[] senderPublicKey = this.repository.getResultSetBytes(rs.getBinaryStream(1));

			List<PaymentData> payments = this.getPaymentsFromSignature(signature);

			return new MultiPaymentTransactionData(senderPublicKey, payments, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch multi-payment transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("MultiPaymentTransactions");

		saveHelper.bind("signature", multiPaymentTransactionData.getSignature()).bind("sender", multiPaymentTransactionData.getSenderPublicKey());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save multi-payment transaction into repository", e);
		}

		// Save payments. If this fails then it is the caller's responsibility to catch the DataException as the underlying transaction will have been lost.
		this.savePayments(transactionData.getSignature(), multiPaymentTransactionData.getPayments());
	}

}
