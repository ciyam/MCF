package org.qora.test.common;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.qora.account.PrivateKeyAccount;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.EnableForgingTransactionData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.ProxyForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AccountUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final BigDecimal fee = BigDecimal.ONE.setScale(8);

	public static void pay(Repository repository, String sender, String recipient, BigDecimal amount) throws DataException {
		PrivateKeyAccount sendingAccount = Common.getTestAccount(repository, sender);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		byte[] reference = sendingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		TransactionData transactionData = new PaymentTransactionData(timestamp, txGroupId, reference, sendingAccount.getPublicKey(), recipientAccount.getAddress(), amount, fee);

		TransactionUtils.signAndForge(repository, transactionData, sendingAccount);
	}

	public static byte[] proxyForging(Repository repository, String forger, String recipient, BigDecimal share) throws DataException {
		PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, forger);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		byte[] reference = forgingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		byte[] proxyPrivateKey = forgingAccount.getSharedSecret(recipientAccount.getPublicKey());
		PrivateKeyAccount proxyAccount = new PrivateKeyAccount(null, proxyPrivateKey);

		TransactionData transactionData = new ProxyForgingTransactionData(timestamp, txGroupId, reference, forgingAccount.getPublicKey(), recipientAccount.getAddress(), proxyAccount.getPublicKey(), share, fee);

		TransactionUtils.signAndForge(repository, transactionData, forgingAccount);

		return proxyPrivateKey;
	}

	public static TransactionData createEnableForging(Repository repository, String forger, byte[] recipientPublicKey) throws DataException {
		PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, forger);

		byte[] reference = forgingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		return new EnableForgingTransactionData(timestamp, txGroupId, reference, forgingAccount.getPublicKey(), Crypto.toAddress(recipientPublicKey), fee);
	}

	public static TransactionData createEnableForging(Repository repository, String forger, String recipient) throws DataException {
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		return createEnableForging(repository, forger, recipientAccount.getPublicKey());
	}

	public static TransactionData enableForging(Repository repository, String forger, String recipient) throws DataException {
		TransactionData transactionData = createEnableForging(repository, forger, recipient);

		PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, forger);
		TransactionUtils.signAndForge(repository, transactionData, forgingAccount);

		return transactionData;
	}

	public static Map<String, Map<Long, BigDecimal>> getBalances(Repository repository, long... assetIds) throws DataException {
		Map<String, Map<Long, BigDecimal>> balances = new HashMap<>();

		for (TestAccount account : Common.getTestAccounts(repository))
			for (Long assetId : assetIds) {
				BigDecimal balance = account.getConfirmedBalance(assetId);

				balances.compute(account.accountName, (key, value) -> {
					if (value == null)
						value = new HashMap<Long, BigDecimal>();

					value.put(assetId, balance);

					return value;
				});
			}

		return balances;
	}

	public static BigDecimal getBalance(Repository repository, String accountName, long assetId) throws DataException {
		return Common.getTestAccount(repository, accountName).getConfirmedBalance(assetId);
	}

	public static void assertBalance(Repository repository, String accountName, long assetId, BigDecimal expectedBalance) throws DataException {
		BigDecimal actualBalance = getBalance(repository, accountName, assetId);
		String assetName = repository.getAssetRepository().fromAssetId(assetId).getName();

		Common.assertEqualBigDecimals(String.format("%s's %s [%d] balance incorrect", accountName, assetName, assetId), expectedBalance, actualBalance);
	}

}
