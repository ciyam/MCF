package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.test.common.AssetUtils;

public class IssueAssetTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String owner = account.getAddress();
		String assetName = "test-asset-" + random.nextInt(1_000_000);
		String description = "random test asset";
		final long quantity = 1_000_000L;
		final boolean isDivisible = true;
		String data = AssetUtils.randomData();

		return new IssueAssetTransactionData(generateBase(account), owner, assetName, description, quantity, isDivisible, data);
	}

}
