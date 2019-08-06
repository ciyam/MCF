package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBPaymentTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBPaymentTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT recipient, amount FROM PaymentTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);
			BigDecimal amount = resultSet.getBigDecimal(2);

			return new PaymentTransactionData(baseTransactionData, recipient, amount);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch payment transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
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
