package org.qora.test.common;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.test.Common;

public class AssetUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final BigDecimal fee = BigDecimal.ONE.setScale(8);
	public static final long testAssetId = 1L;

	public static void transferAsset(Repository repository, String fromAccountName, String toAccountName, long assetId, long amount) throws DataException {
		PrivateKeyAccount fromAccount = Common.getTestAccount(repository, fromAccountName);
		PrivateKeyAccount toAccount = Common.getTestAccount(repository, toAccountName);

		byte[] reference = fromAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		TransactionData transferAssetTransactionData = new TransferAssetTransactionData(timestamp, AssetUtils.txGroupId, reference, fromAccount.getPublicKey(), toAccount.getAddress(), BigDecimal.valueOf(amount), assetId, AssetUtils.fee);

		Common.signAndForge(repository, transferAssetTransactionData, fromAccount);
	}

	public static void createOrder(Repository repository, String accountName, long haveAssetId, long wantAssetId, long haveAmount, long wantAmount) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, accountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;
		BigDecimal amount = BigDecimal.valueOf(haveAmount);
		BigDecimal price = BigDecimal.valueOf(wantAmount);

		// Note: "price" is not the same in V2 as in V1
		TransactionData initialOrderTransactionData = new CreateAssetOrderTransactionData(timestamp, txGroupId, reference, account.getPublicKey(), haveAssetId, wantAssetId, amount, price, fee);

		Common.signAndForge(repository, initialOrderTransactionData, account);
	}

}
