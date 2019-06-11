package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CreateAssetOrderTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final long haveAssetId = Asset.QORA;
		final long wantAssetId = 1;
		BigDecimal amount = BigDecimal.valueOf(123);
		BigDecimal price = BigDecimal.valueOf(123);

		return new CreateAssetOrderTransactionData(generateBase(account), haveAssetId, wantAssetId, amount, price);
	}

}
