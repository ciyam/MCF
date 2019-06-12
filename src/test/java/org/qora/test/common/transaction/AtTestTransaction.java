package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AtTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		byte[] signature = new byte[64];
		random.nextBytes(signature);
		String atAddress = Crypto.toATAddress(signature);
		String recipient = account.getAddress();
		BigDecimal amount = BigDecimal.valueOf(123);
		final long assetId = Asset.QORA;
		byte[] message = new byte[32];
		random.nextBytes(message);

		return new ATTransactionData(generateBase(account), atAddress, recipient, amount, assetId, message);
	}

}
