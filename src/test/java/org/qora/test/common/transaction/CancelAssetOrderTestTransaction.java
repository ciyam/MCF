package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CancelAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CancelAssetOrderTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();
		byte[] orderId = new byte[64];
		random.nextBytes(orderId);

		return new CancelAssetOrderTransactionData(generateBase(account), orderId);
	}

}
