package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class TransferAssetTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String recipient = account.getAddress();
		final long assetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(123);

		return new TransferAssetTransactionData(generateBase(account), recipient, amount, assetId);
	}

}
