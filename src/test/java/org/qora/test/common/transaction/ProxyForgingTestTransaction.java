package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.ProxyForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class ProxyForgingTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String recipient = account.getAddress();
		byte[] proxyPublicKey = account.getProxyPrivateKey(account.getPublicKey());
		BigDecimal share = BigDecimal.valueOf(50);

		return new ProxyForgingTransactionData(generateBase(account), recipient, proxyPublicKey, share);
	}

}
