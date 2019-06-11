package org.qora.test.common;

import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Map;
import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.asset.OrderData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.CancelAssetOrderTransactionData;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;

public class AssetUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final BigDecimal fee = BigDecimal.ONE.setScale(8);

	public static final long testAssetId = 1L; // Owned by Alice
	public static final long otherAssetId = 2L; // Owned by Bob
	public static final long goldAssetId = 3L; // Owned by Alice

	public static long issueAsset(Repository repository, String issuerAccountName, String assetName, long quantity, boolean isDivisible) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, issuerAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, AssetUtils.txGroupId, reference, account.getPublicKey(), AssetUtils.fee, null);
		TransactionData transactionData = new IssueAssetTransactionData(baseTransactionData, account.getAddress(), assetName, "desc", quantity, isDivisible, "{}");

		TransactionUtils.signAndForge(repository, transactionData, account);

		return repository.getAssetRepository().fromAssetName(assetName).getAssetId();
	}

	public static void transferAsset(Repository repository, String fromAccountName, String toAccountName, long assetId, BigDecimal amount) throws DataException {
		PrivateKeyAccount fromAccount = Common.getTestAccount(repository, fromAccountName);
		PrivateKeyAccount toAccount = Common.getTestAccount(repository, toAccountName);

		byte[] reference = fromAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, AssetUtils.txGroupId, reference, fromAccount.getPublicKey(), AssetUtils.fee, null);
		TransactionData transactionData = new TransferAssetTransactionData(baseTransactionData, toAccount.getAddress(), amount, assetId);

		TransactionUtils.signAndForge(repository, transactionData, fromAccount);
	}

	public static byte[] createOrder(Repository repository, String accountName, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, accountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, AssetUtils.txGroupId, reference, account.getPublicKey(), AssetUtils.fee, null);
		TransactionData transactionData = new CreateAssetOrderTransactionData(baseTransactionData, haveAssetId, wantAssetId, amount, price);

		TransactionUtils.signAndForge(repository, transactionData, account);

		return repository.getAssetRepository().getAccountsOrders(account.getPublicKey(), null, null, null, null, true).get(0).getOrderId();
	}

	public static void cancelOrder(Repository repository, String accountName, byte[] orderId) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, accountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, AssetUtils.txGroupId, reference, account.getPublicKey(), AssetUtils.fee, null);
		TransactionData transactionData = new CancelAssetOrderTransactionData(baseTransactionData, orderId);

		TransactionUtils.signAndForge(repository, transactionData, account);
	}

	public static void genericTradeTest(long haveAssetId, long wantAssetId,
			BigDecimal aliceAmount, BigDecimal alicePrice,
			BigDecimal bobAmount, BigDecimal bobPrice,
			BigDecimal aliceCommitment, BigDecimal bobCommitment,
			BigDecimal aliceReturn, BigDecimal bobReturn, BigDecimal bobSaving) throws DataException {
		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, haveAssetId, wantAssetId);

			// Create target order
			byte[] targetOrderId = createOrder(repository, "alice", haveAssetId, wantAssetId, aliceAmount, alicePrice);

			// Create initiating order
			byte[] initiatingOrderId = createOrder(repository, "bob", wantAssetId, haveAssetId, bobAmount, bobPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;
			OrderData targetOrderData = repository.getAssetRepository().fromOrderId(targetOrderId);
			OrderData initiatingOrderData = repository.getAssetRepository().fromOrderId(initiatingOrderId);

			boolean isNewPricing = initiatingOrderData.getTimestamp() > BlockChain.getInstance().getNewAssetPricingTimestamp();

			// Alice selling have asset
			expectedBalance = initialBalances.get("alice").get(haveAssetId).subtract(aliceCommitment);
			AccountUtils.assertBalance(repository, "alice", haveAssetId, expectedBalance);

			// Alice buying want asset
			expectedBalance = initialBalances.get("alice").get(wantAssetId).add(aliceReturn);
			AccountUtils.assertBalance(repository, "alice", wantAssetId, expectedBalance);

			// Bob selling want asset
			expectedBalance = initialBalances.get("bob").get(wantAssetId).subtract(bobCommitment).add(bobSaving);
			AccountUtils.assertBalance(repository, "bob", wantAssetId, expectedBalance);

			// Bob buying have asset
			expectedBalance = initialBalances.get("bob").get(haveAssetId).add(bobReturn);
			AccountUtils.assertBalance(repository, "bob", haveAssetId, expectedBalance);

			// Check orders
			BigDecimal expectedFulfilled;
			BigDecimal newPricingAmount = (initiatingOrderData.getHaveAssetId() < initiatingOrderData.getWantAssetId()) ? bobReturn : aliceReturn;

			// Check matching order
			assertNotNull("matching order missing", initiatingOrderData);
			expectedFulfilled = isNewPricing ? newPricingAmount : aliceReturn;
			Common.assertEqualBigDecimals(String.format("Bob's order \"fulfilled\" incorrect"), expectedFulfilled, initiatingOrderData.getFulfilled());

			// Check initial order
			assertNotNull("initial order missing", targetOrderData);
			expectedFulfilled = isNewPricing ? newPricingAmount : bobReturn;
			Common.assertEqualBigDecimals(String.format("Alice's order \"fulfilled\" incorrect"), expectedFulfilled, targetOrderData.getFulfilled());
		}
	}

	public static String randomData() {
		Random random = new Random();
		byte[] rawData = new byte[1024];
		random.nextBytes(rawData);

		Encoder base64Encoder = Base64.getEncoder();
		return "{ \"logo\": \"data:image/png;base64," + base64Encoder.encodeToString(rawData) + "\" }";
	}

}
