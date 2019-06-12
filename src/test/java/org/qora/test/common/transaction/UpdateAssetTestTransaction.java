package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.UpdateAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.test.common.AssetUtils;

public class UpdateAssetTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final long assetId = 1;
		String newOwner = account.getAddress();
		String newDescription = "updated random test asset";
		String newData = AssetUtils.randomData();

		return new UpdateAssetTransactionData(generateBase(account), assetId, newOwner, newDescription, newData);
	}

}
