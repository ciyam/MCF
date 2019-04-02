package org.qora.test.common;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.IssueAssetTransactionData;
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

	public static long issueAsset(Repository repository, String issuerAccountName, String assetName, long quantity, boolean isDivisible) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, issuerAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		TransactionData transactionData = new IssueAssetTransactionData(timestamp, AssetUtils.txGroupId, reference, account.getPublicKey(), account.getAddress(), assetName, "desc", quantity, isDivisible, "{}", AssetUtils.fee);

		Common.signAndForge(repository, transactionData, account);

		return repository.getAssetRepository().fromAssetName(assetName).getAssetId();
	}

	public static void transferAsset(Repository repository, String fromAccountName, String toAccountName, long assetId, BigDecimal amount) throws DataException {
		PrivateKeyAccount fromAccount = Common.getTestAccount(repository, fromAccountName);
		PrivateKeyAccount toAccount = Common.getTestAccount(repository, toAccountName);

		byte[] reference = fromAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		TransactionData transactionData = new TransferAssetTransactionData(timestamp, AssetUtils.txGroupId, reference, fromAccount.getPublicKey(), toAccount.getAddress(), amount, assetId, AssetUtils.fee);

		Common.signAndForge(repository, transactionData, fromAccount);
	}

	public static byte[] createOrder(Repository repository, String accountName, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal wantAmount) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, accountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1000;

		// Note: "price" is not the same in V2 as in V1
		TransactionData transactionData = new CreateAssetOrderTransactionData(timestamp, txGroupId, reference, account.getPublicKey(), haveAssetId, wantAssetId, amount, wantAmount, fee);

		Common.signAndForge(repository, transactionData, account);

		return repository.getAssetRepository().getAccountsOrders(account.getPublicKey(), null, null, null, null, true).get(0).getOrderId();
	}

}
