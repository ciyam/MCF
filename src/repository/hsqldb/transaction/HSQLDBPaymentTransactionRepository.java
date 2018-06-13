package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBPaymentTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBPaymentTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT sender, recipient, amount FROM PaymentTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			byte[] senderPublicKey = this.repository.getResultSetBytes(rs.getBinaryStream(1));
			String recipient = rs.getString(2);
			BigDecimal amount = rs.getBigDecimal(3);

			return new PaymentTransactionData(senderPublicKey, recipient, amount, fee, timestamp, reference, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch payment transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		super.save(transactionData);

		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("PaymentTransactions");

		saveHelper.bind("signature", paymentTransactionData.getSignature()).bind("sender", paymentTransactionData.getSenderPublicKey())
				.bind("recipient", paymentTransactionData.getRecipient()).bind("amount", paymentTransactionData.getAmount());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save payment transaction into repository", e);
		}
	}

}
