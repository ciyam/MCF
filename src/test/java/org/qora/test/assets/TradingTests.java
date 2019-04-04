package org.qora.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.asset.Asset;
import org.qora.asset.Order;
import org.qora.block.BlockChain;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.AssetUtils;
import org.qora.test.common.Common;

import java.math.BigDecimal;
import java.util.Map;

public class TradingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	/**
	 * Check granularity adjustment values.
	 * <p>
	 * If trading at a price of 12 eggs for 1 coin
	 * then trades can only happen at multiples of
	 * 0.000000001 or 0.00000012 depending on direction.
	 */
	@Test
	public void testDivisibleGranularities() {
		testGranularity(true, true, "12", "1", "0.00000012");
		testGranularity(true, true, "1", "12", "0.00000001");
	}

	/**
	 * Check granularity adjustment values.
	 * <p>
	 * If trading at a price of 123 riches per 50301 rags,
	 * then the GCD(123, 50301) is 3 and so trades can only
	 * happen at multiples of (50301/3) = 16767 rags or
	 * (123/3) = 41 riches.
	 */
	@Test
	public void testIndivisibleGranularities() {
		testGranularity(false, false, "50301", "123", "16767");
		testGranularity(false, false, "123", "50301", "41");
	}

	private void testGranularity(boolean isOurHaveDivisible, boolean isOurWantDivisible, String theirHaveAmount, String theirWantAmount, String expectedGranularity) {
		final long newPricingTimestamp = BlockChain.getInstance().getNewAssetPricingTimestamp() + 1;

		final AssetData ourHaveAssetData = new AssetData(null, null, null, 0, isOurHaveDivisible, null, 0, null);
		final AssetData ourWantAssetData = new AssetData(null, null, null, 0, isOurWantDivisible, null, 0, null);

		OrderData theirOrderData = new OrderData(null, null, 0, 0, new BigDecimal(theirHaveAmount), new BigDecimal(theirWantAmount), null, newPricingTimestamp);

		BigDecimal granularity = Order.calculateAmountGranularity(ourHaveAssetData, ourWantAssetData, theirOrderData);
		assertEqualBigDecimals("Granularity incorrect", new BigDecimal(expectedGranularity), granularity);
	}

	/**
	 * Check matching of indivisible amounts.
	 * <p>
	 * New pricing scheme allows two attempts are calculating matched amount
	 * to reduce partial-match issues caused by rounding and recurring fractional digits:
	 * <p>
	 * <ol>
	 * <li> amount * round_down(1 / unit price) </li>
	 * <li> round_down(amount / unit price) </li>
	 * </ol>
	 * Alice's price is 12 QORA per ATNL so the ATNL per QORA unit price is 0.08333333...<br>
	 * Bob wants to spend 24 QORA so:
	 * <p>
	 * <ol>
	 * <li> 24 QORA * (1 / 0.0833333...) = 1.99999999 ATNL </li>
	 * <li> 24 QORA / 0.08333333.... = 2 ATNL </li>
	 * </ol>
	 * The second result is obviously more intuitive as is critical where assets are not divisible,
	 * like ATNL in this test case.
	 * <p>
	 * @see TradingTests#testOldNonExactFraction
	 * @see TradingTests#testNonExactFraction
	 * @throws DataException
	 */
	@Test
	public void testMixedDivisibility() throws DataException {
		// Issue indivisible asset
		long atnlAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			atnlAssetId = AssetUtils.issueAsset(repository, "alice", "ATNL", 100000000L, false);
		}

		final BigDecimal atnlAmount = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal qoraAmount = BigDecimal.valueOf(24L).setScale(8);

		genericTradeTest(atnlAssetId, Asset.QORA, atnlAmount, qoraAmount, qoraAmount, atnlAmount, atnlAmount, qoraAmount);
	}

	/**
	 * Check matching of indivisible amounts (new pricing).
	 * <p>
	 * Alice is selling twice as much as Bob wants,
	 * but at the same [calculated] unit price,
	 * so Bob's order should fully match.
	 * <p>
	 * However, in legacy/"old" mode, the granularity checks
	 * would prevent this trade.
	 */
	@Test
	public void testIndivisible() throws DataException {
		// Issue some indivisible assets
		long ragsAssetId;
		long richesAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisble asset
			ragsAssetId = AssetUtils.issueAsset(repository, "alice", "rags", 12345678L, false);

			// Issue another indivisble asset
			richesAssetId = AssetUtils.issueAsset(repository, "bob", "riches", 87654321L, false);
		}

		final BigDecimal ragsAmount = BigDecimal.valueOf(50301L).setScale(8);
		final BigDecimal richesAmount = BigDecimal.valueOf(123L).setScale(8);

		final BigDecimal two = BigDecimal.valueOf(2L);

		genericTradeTest(ragsAssetId, richesAssetId, ragsAmount.multiply(two), richesAmount.multiply(two), richesAmount, ragsAmount, ragsAmount, richesAmount);
	}

	/**
	 * Check matching of indivisible amounts.
	 * <p>
	 * We use orders similar to some found in legacy qora1 blockchain
	 * to test for expected results with indivisible assets.
	 * <p>
	 * In addition, although the 3rd "further" order would match up to 999 RUB.iPLZ,
	 * granularity at that price reduces matched amount to 493 RUB.iPLZ.
	 */
	@Test
	public void testOldIndivisible() throws DataException {
		Common.useSettings("test-settings-old-asset.json");

		// Issue some indivisible assets
		long asset112Id;
		long asset113Id;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisble asset
			asset112Id = AssetUtils.issueAsset(repository, "alice", "RUB.iPLZ", 999999999999L, false);

			// Issue another indivisble asset
			asset113Id = AssetUtils.issueAsset(repository, "bob", "RU.GZP.V123", 10000L, false);
		}

		// Transfer some assets so orders can be created
		try (Repository repository = RepositoryManager.getRepository()) {
			AssetUtils.transferAsset(repository, "alice", "bob", asset112Id, BigDecimal.valueOf(5000L).setScale(8));
			AssetUtils.transferAsset(repository, "bob", "alice", asset113Id, BigDecimal.valueOf(5000L).setScale(8));
		}

		final BigDecimal asset113Amount = new BigDecimal("1000").setScale(8);
		final BigDecimal asset112Price = new BigDecimal("1.00000000").setScale(8);

		final BigDecimal asset112Amount = new BigDecimal("2000").setScale(8);
		final BigDecimal asset113Price = new BigDecimal("0.98600000").setScale(8);

		final BigDecimal asset112Matched = new BigDecimal("1000").setScale(8);
		final BigDecimal asset113Matched = new BigDecimal("1000").setScale(8);

		genericTradeTest(asset113Id, asset112Id, asset113Amount, asset112Price, asset112Amount, asset113Price, asset113Matched, asset112Matched);

		// Further trade
		final BigDecimal asset113Amount2 = new BigDecimal("986").setScale(8);
		final BigDecimal asset112Price2 = new BigDecimal("1.00000000").setScale(8);

		final BigDecimal asset112Matched2 = new BigDecimal("500").setScale(8);
		final BigDecimal asset113Matched2 = new BigDecimal("493").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, asset112Id, asset113Id);

			// Create further order
			byte[] furtherOrderId = AssetUtils.createOrder(repository, "alice", asset113Id, asset112Id, asset113Amount2, asset112Price2);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// Alice asset 113
			expectedBalance = initialBalances.get("alice").get(asset113Id).subtract(asset113Amount2);
			assertBalance(repository, "alice", asset113Id, expectedBalance);

			// Alice asset 112
			expectedBalance = initialBalances.get("alice").get(asset112Id).add(asset112Matched2);
			assertBalance(repository, "alice", asset112Id, expectedBalance);

			BigDecimal expectedFulfilled = asset113Matched2;
			BigDecimal actualFulfilled = repository.getAssetRepository().fromOrderId(furtherOrderId).getFulfilled();
			assertEqualBigDecimals("Order fulfilled incorrect", expectedFulfilled, actualFulfilled);
		}
	}

	/**
	 * Check full matching of orders with prices that
	 * can't be represented in floating binary.
	 * <p>
	 * For example, sell 1 GOLD for 12 QORA so
	 * price is 1/12 or 0.08333333..., which could
	 * lead to rounding issues or inexact match amounts,
	 * but we counter this using the technique described in
	 * {@link #testMixedDivisibility()}
	 */
	@Test
	public void testNonExactFraction() throws DataException {
		final BigDecimal otherAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal qoraAmount = BigDecimal.valueOf(2L).setScale(8);

		genericTradeTest(AssetUtils.testAssetId, Asset.QORA, otherAmount, qoraAmount, qoraAmount, otherAmount, otherAmount, qoraAmount);
	}

	/**
	 * Check legacy partial matching of orders with prices that
	 * can't be represented in floating binary.
	 * <p>
	 * For example, sell 2 TEST for 24 QORA so
	 * unit price is 2 / 24 or 0.08333333.
	 * <p>
	 * This inexactness causes the match amount to be
	 * only 1.99999992 instead of the expected 2.00000000.
	 * <p>
	 * However this behaviour is "grandfathered" in legacy/"old"
	 * mode so we need to test.
	 */
	@Test
	public void testOldNonExactFraction() throws DataException {
		Common.useSettings("test-settings-old-asset.json");

		final BigDecimal initialAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal initialPrice = new BigDecimal("0.08333333").setScale(8);

		final BigDecimal matchedAmount = new BigDecimal("2.00000000").setScale(8);
		final BigDecimal matchedPrice = new BigDecimal("12.00000000").setScale(8);

		// Due to rounding these are the expected traded amounts.
		final BigDecimal tradedQoraAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal tradedOtherAmount = new BigDecimal("1.99999992").setScale(8);

		genericTradeTest(AssetUtils.testAssetId, Asset.QORA, initialAmount, initialPrice, matchedAmount, matchedPrice, tradedQoraAmount, tradedOtherAmount);
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovement() throws DataException {
		final BigDecimal qoraAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal betterQoraAmount = BigDecimal.valueOf(25L).setScale(8);
		final BigDecimal bestQoraAmount = BigDecimal.valueOf(31L).setScale(8);

		final BigDecimal otherAmount = BigDecimal.valueOf(2L).setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA, AssetUtils.testAssetId);

			// Create best initial order
			AssetUtils.createOrder(repository, "bob", Asset.QORA, AssetUtils.testAssetId, qoraAmount, otherAmount);

			// Create initial order better than first
			AssetUtils.createOrder(repository, "chloe", Asset.QORA, AssetUtils.testAssetId, bestQoraAmount, otherAmount);

			// Create initial order
			AssetUtils.createOrder(repository, "dilbert", Asset.QORA, AssetUtils.testAssetId, betterQoraAmount, otherAmount);

			// Create matching order
			AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, Asset.QORA, otherAmount, qoraAmount);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)

			// Alice Qora
			expectedBalance = initialBalances.get("alice").get(Asset.QORA).add(bestQoraAmount);
			assertBalance(repository, "alice", Asset.QORA, expectedBalance);

			// Alice test asset
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(otherAmount);
			assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			// Bob Qora
			expectedBalance = initialBalances.get("bob").get(Asset.QORA).subtract(qoraAmount);
			assertBalance(repository, "bob", Asset.QORA, expectedBalance);

			// Bob test asset
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId);
			assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe Qora
			expectedBalance = initialBalances.get("chloe").get(Asset.QORA).subtract(bestQoraAmount);
			assertBalance(repository, "chloe", Asset.QORA, expectedBalance);

			// Chloe test asset
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId).add(otherAmount);
			assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert Qora
			expectedBalance = initialBalances.get("dilbert").get(Asset.QORA).subtract(betterQoraAmount);
			assertBalance(repository, "dilbert", Asset.QORA, expectedBalance);

			// Dilbert test asset
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.testAssetId);
			assertBalance(repository, "dilbert", AssetUtils.testAssetId, expectedBalance);
		}
	}

	/**
	 * Check legacy qora1 blockchain matching behaviour.
	 */
	@Test
	public void testQora1Compat() throws DataException {
		// Asset 61 [ATFunding] was issued by QYsLsfwMRBPnunmuWmFkM4hvGsfooY8ssU with 250,000,000 quantity and was divisible.

		// Initial order 2jMinWSBjxaLnQvhcEoWGs2JSdX7qbwxMTZenQXXhjGYDHCJDL6EjXPz5VXYuUfZM5LvRNNbcaeBbM6Xhb4tN53g
		// Creator was QZyuTa3ygjThaPRhrCp1BW4R5Sed6uAGN8 at 2014-10-23 11:14:42.525000+0:00
		// Have: 150000 [ATFunding], Price: 1.7000000 QORA

		// Matching order 3Ufqi52nDL3Gi7KqVXpgebVN5FmLrdq2XyUJ11BwSV4byxQ2z96Q5CQeawGyanhpXS4XkYAaJTrNxsDDDxyxwbMN
		// Creator was QMRoD3RS5vJ4DVNBhBgGtQG4KT3PhkNALH at 2015-03-27 12:24:02.945000+0:00
		// Have: 2 QORA, Price: 0.58 [ATFunding]

		// Trade: 1.17647050 [ATFunding] for 1.99999985 QORA

		// Load/check settings, which potentially sets up blockchain config, etc.
		Common.useSettings("test-settings-old-asset.json");

		// Transfer some test asset to bob
		try (Repository repository = RepositoryManager.getRepository()) {
			AssetUtils.transferAsset(repository, "alice", "bob", AssetUtils.testAssetId, BigDecimal.valueOf(200000L).setScale(8));
		}

		final BigDecimal initialAmount = new BigDecimal("150000").setScale(8);
		final BigDecimal initialPrice = new BigDecimal("1.70000000").setScale(8);

		final BigDecimal matchingAmount = new BigDecimal("2.00000000").setScale(8);
		final BigDecimal matchingPrice = new BigDecimal("0.58000000").setScale(8);

		final BigDecimal tradedOtherAmount = new BigDecimal("1.17647050").setScale(8);
		final BigDecimal tradedQoraAmount = new BigDecimal("1.99999985").setScale(8);

		genericTradeTest(AssetUtils.testAssetId, Asset.QORA, initialAmount, initialPrice, matchingAmount, matchingPrice, tradedOtherAmount, tradedQoraAmount);
	}

	/**
	 * Check legacy qora1 blockchain matching behaviour.
	 */
	@Test
	public void testQora1Compat2() throws DataException {
		// Asset 95 [Bitcoin] was issued by QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj with 21000000 quantity and was divisible.
		// Asset 96 [BitBTC] was issued by QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj with 21000000 quantity and was divisible.

		// Initial order 3jinKPHEak9xrjeYtCaE1PawwRZeRkhYA6q4A7sqej7f3jio8WwXwXpfLWVZkPQ3h6cVdwPhcDFNgbbrBXcipHee
		// Creator was QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj at 2015-06-10 20:31:44.840000+0:00
		// Have: 1000000 [BitBTC], Price: 0.90000000 [Bitcoin]

		// Matching order Jw1UfgspZ344waF8qLhGJanJXVa32FBoVvMW5ByFkyHvZEumF4fPqbaGMa76ba1imC4WX5t3Roa7r23Ys6rhKAA
		// Creator was QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj at 2015-06-14 17:49:41.410000+0:00
		// Have: 73251 [Bitcoin], Price: 1.01 [BitBTC]

		// Trade: 81389.99991860 [BitBTC] for 73250.99992674 [Bitcoin]

		// Load/check settings, which potentially sets up blockchain config, etc.
		Common.useSettings("test-settings-old-asset.json");

		// Transfer some test asset to bob
		try (Repository repository = RepositoryManager.getRepository()) {
			AssetUtils.transferAsset(repository, "alice", "bob", AssetUtils.testAssetId, BigDecimal.valueOf(200000L).setScale(8));
		}

		final BigDecimal initialAmount = new BigDecimal("1000000").setScale(8);
		final BigDecimal initialPrice = new BigDecimal("0.90000000").setScale(8);

		final BigDecimal matchingAmount = new BigDecimal("73251").setScale(8);
		final BigDecimal matchingPrice = new BigDecimal("1.01000000").setScale(8);

		final BigDecimal tradedHaveAmount = new BigDecimal("81389.99991860").setScale(8);
		final BigDecimal tradedWantAmount = new BigDecimal("73250.99992674").setScale(8);

		genericTradeTest(Asset.QORA, AssetUtils.testAssetId, initialAmount, initialPrice, matchingAmount, matchingPrice, tradedHaveAmount, tradedWantAmount);
	}

	private void genericTradeTest(long haveAssetId, long wantAssetId,
			BigDecimal initialAmount, BigDecimal initialPrice,
			BigDecimal matchingAmount, BigDecimal matchingPrice,
			BigDecimal tradedHaveAmount, BigDecimal tradedWantAmount) throws DataException {
		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, haveAssetId, wantAssetId);

			// Create initial order
			AssetUtils.createOrder(repository, "alice", haveAssetId, wantAssetId, initialAmount, initialPrice);

			// Create matching order
			AssetUtils.createOrder(repository, "bob", wantAssetId, haveAssetId, matchingAmount, matchingPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// Alice have asset
			expectedBalance = initialBalances.get("alice").get(haveAssetId).subtract(initialAmount);
			assertBalance(repository, "alice", haveAssetId, expectedBalance);

			// Alice want asset
			expectedBalance = initialBalances.get("alice").get(wantAssetId).add(tradedWantAmount);
			assertBalance(repository, "alice", wantAssetId, expectedBalance);

			// Bob want asset
			expectedBalance = initialBalances.get("bob").get(wantAssetId).subtract(matchingAmount);
			assertBalance(repository, "bob", wantAssetId, expectedBalance);

			// Bob have asset
			expectedBalance = initialBalances.get("bob").get(haveAssetId).add(tradedHaveAmount);
			assertBalance(repository, "bob", haveAssetId, expectedBalance);
		}
	}

	private static void assertBalance(Repository repository, String accountName, long assetId, BigDecimal expectedBalance) throws DataException {
		BigDecimal actualBalance = Common.getTestAccount(repository, accountName).getConfirmedBalance(assetId);

		assertEqualBigDecimals(String.format("Test account '%s' asset %d balance incorrect", accountName, assetId), expectedBalance, actualBalance);
	}

}