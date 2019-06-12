package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class MessageTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int version = 3;
		String recipient = account.getAddress();
		final long assetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(123L);
		byte[] data = "message contents".getBytes();
		final boolean isText = true;
		final boolean isEncrypted = false;

		return new MessageTransactionData(generateBase(account), version, recipient, assetId, amount, data, isText, isEncrypted);
	}

}
