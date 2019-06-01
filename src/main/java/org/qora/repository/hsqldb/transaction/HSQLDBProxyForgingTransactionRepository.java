package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.ProxyForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;

public class HSQLDBProxyForgingTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBProxyForgingTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		final String sql = "SELECT recipient, proxy_public_key, share, previous_share FROM ProxyForgingTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);
			byte[] proxyPublicKey = resultSet.getBytes(2);
			BigDecimal share = resultSet.getBigDecimal(3);
			BigDecimal previousShare = resultSet.getBigDecimal(4);

			return new ProxyForgingTransactionData(baseTransactionData, recipient, proxyPublicKey, share, previousShare);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch proxy forging transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		ProxyForgingTransactionData proxyForgingTransactionData = (ProxyForgingTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("ProxyForgingTransactions");

		saveHelper.bind("signature", proxyForgingTransactionData.getSignature()).bind("forger", proxyForgingTransactionData.getForgerPublicKey())
				.bind("recipient", proxyForgingTransactionData.getRecipient()).bind("proxy_public_key", proxyForgingTransactionData.getProxyPublicKey())
				.bind("share", proxyForgingTransactionData.getShare()).bind("previous_share", proxyForgingTransactionData.getPreviousShare());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save proxy forging transaction into repository", e);
		}
	}

}
