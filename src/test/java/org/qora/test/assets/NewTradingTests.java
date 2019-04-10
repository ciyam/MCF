package org.qora.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.qora.asset.Asset;
import org.qora.data.asset.OrderData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.AssetUtils;
import org.qora.test.common.Common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class NewTradingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimple() throws DataException {
		final BigDecimal testAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal price = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal qoraAmount = BigDecimal.valueOf(48L).setScale(8);

		// amounts are in test-asset
		// prices are in qora/test

		final BigDecimal aliceAmount = testAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = testAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = testAmount;
		final BigDecimal bobCommitment = qoraAmount;

		final BigDecimal aliceReturn = qoraAmount;
		final BigDecimal bobReturn = testAmount;

		// alice (target) order: have 'testAmount' test, want qora @ 'price' qora/test (commits testAmount test)
		// bob (initiating) order: have qora, want 'testAmount' test @ 'price' qora/test (commits testAmount*price = qoraAmount)
		// Alice should be -testAmount, +qoraAmount
		// Bob should be -qoraAmount, +testAmount

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
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
	 * @see NewTradingTests#testOldNonExactFraction
	 * @see NewTradingTests#testNonExactFraction
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
		final BigDecimal price = qoraAmount.divide(atnlAmount, RoundingMode.DOWN);

		// amounts are in ATNL
		// prices are in qora/ATNL

		final BigDecimal aliceAmount = atnlAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = atnlAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = atnlAmount;
		final BigDecimal bobCommitment = qoraAmount;

		final BigDecimal aliceReturn = qoraAmount;
		final BigDecimal bobReturn = atnlAmount;

		AssetUtils.genericTradeTest(atnlAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
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
			// Issue indivisible asset
			ragsAssetId = AssetUtils.issueAsset(repository, "alice", "rags", 1000000L, false);

			// Issue another indivisible asset
			richesAssetId = AssetUtils.issueAsset(repository, "bob", "riches", 1000000L, false);
		}

		// "amount" will be in riches, "price" will be in rags/riches

		final BigDecimal ragsAmount = BigDecimal.valueOf(50307L).setScale(8);
		final BigDecimal richesAmount = BigDecimal.valueOf(123L).setScale(8);

		final BigDecimal price = ragsAmount.divide(richesAmount, RoundingMode.DOWN);
		final BigDecimal two = BigDecimal.valueOf(2L);

		final BigDecimal aliceAmount = richesAmount.multiply(two).setScale(8);
		final BigDecimal alicePrice = price;
		final BigDecimal aliceCommitment = aliceAmount.multiply(alicePrice).setScale(8); // rags

		final BigDecimal bobAmount = richesAmount;
		final BigDecimal bobPrice = price;
		final BigDecimal bobCommitment = bobAmount; // riches

		final BigDecimal aliceReturn = bobAmount; // riches
		final BigDecimal bobReturn = bobAmount.multiply(alicePrice).setScale(8); // rags

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

	/**
	 * Check partial matching of indivisible amounts (new pricing).
	 * <p>
	 * Assume both "rags" and "riches" assets are indivisible.
	 * 
	 * Alice places an order:
	 * Have rags, want riches, amount 3 riches, price 1 rags/riches
	 * 
	 * Alice has 1 * 3 = 3 rags subtracted from their rags balance.
	 * 
	 * Bob places an order:
	 * Have riches, want rags, amount 8 riches, price 0.25 rags/riches
	 * 
	 * Bob has 8 riches subtracted from their riches balance.
	 * Bob expects at least 8 * 0.25 = 2 rags if his order fully completes.
	 * 
	 * Alice is offering more rags for riches than Bob expects.
	 * So Alice's order is a match for Bob's, and Alice's order price is used.
	 * 
	 * Bob wants to trade 8 riches, but Alice only wants to trade 3 riches,
	 * so the matched amount is 3 riches.
	 * 
	 * Bob gains 3 * 1 = 3 rags and Alice gains 3 riches.
	 * Alice's order has 0 riches left (fully completed).
	 * 
	 * Bob's order has 8 - 3 = 5 riches left.
	 * 
	 * At Bob's order's price of 0.25 rags/riches,
	 * it would take 1.25 rags to complete the rest of Bob's order.
	 * But rags are indivisible so this can't happen at that price.
	 * 
	 * However, someone could buy at a better price, e.g. 0.4 rags/riches,
	 * trading 2 rags for 5 riches.
	 * 
	 * Or Bob could cancel the rest of his order and be refunded 5 riches.
	 */
	@Test
	public void testPartialIndivisible() throws DataException {
		// Issue some indivisible assets
		long ragsAssetId;
		long richesAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			ragsAssetId = AssetUtils.issueAsset(repository, "alice", "rags", 1000000L, false);

			// Issue another indivisible asset
			richesAssetId = AssetUtils.issueAsset(repository, "bob", "riches", 1000000L, false);
		}

		// "amount" will be in riches, "price" will be in rags/riches

		final BigDecimal aliceAmount = new BigDecimal("3").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("1").setScale(8);
		final BigDecimal aliceCommitment = aliceAmount.multiply(alicePrice).setScale(8); // rags

		final BigDecimal bobAmount = new BigDecimal("8").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("0.25").setScale(8);
		final BigDecimal bobCommitment = bobAmount; // riches

		final BigDecimal aliceReturn = aliceAmount; // riches
		final BigDecimal bobReturn = aliceAmount.multiply(alicePrice).setScale(8);

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

	/**
	 * Check matching of orders with prices that
	 * would have had reciprocals that can't be represented in floating binary.
	 * <p>
	 * For example, sell 2 TEST for 24 OTHER so
	 * unit price is 2 / 24 or 0.08333333(recurring) TEST/OTHER.
	 * <p>
	 * But although price is rounded down to 0.08333333,
	 * the price is the same for both sides.
	 * <p>
	 * Traded amounts are expected to be 24 OTHER
	 * and 1.99999992 TEST.
	 */
	@Test
	public void testNonExactFraction() throws DataException {
		final BigDecimal aliceAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("0.08333333").setScale(8);
		final BigDecimal aliceCommitment = new BigDecimal("1.99999992").setScale(8);

		final BigDecimal bobAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("0.08333333").setScale(8);
		final BigDecimal bobCommitment = new BigDecimal("24.00000000").setScale(8);

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("24.00000000").setScale(8); // other
		final BigDecimal bobReturn = new BigDecimal("1.99999992").setScale(8); // test

		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "other", 5000L, true);
		}

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovement() throws DataException {
		final BigDecimal initialTestAssetAmount = new BigDecimal("24.00000000").setScale(8);

		final BigDecimal basePrice = new BigDecimal("1.00000000").setScale(8);
		final BigDecimal betterPrice = new BigDecimal("2.10000000").setScale(8);
		final BigDecimal bestPrice = new BigDecimal("2.40000000").setScale(8);

		final BigDecimal minimalPrice = new BigDecimal("0.00000001").setScale(8);
		final BigDecimal matchingTestAssetAmount = new BigDecimal("12.00000000").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA, AssetUtils.testAssetId);

			// Create 'better' initial order
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, bestPrice);

			// Create 'base' initial order
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, basePrice);

			// Create matching order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, Asset.QORA, matchingTestAssetAmount, minimalPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			BigDecimal matchedQoraAmount = matchingTestAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN);
			BigDecimal tradedTestAssetAmount = matchingTestAssetAmount;

			// Alice Qora
			expectedBalance = initialBalances.get("alice").get(Asset.QORA).add(matchedQoraAmount);
			AccountUtils.assertBalance(repository, "alice", Asset.QORA, expectedBalance);

			// Alice test asset
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(matchingTestAssetAmount);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			// Bob Qora
			expectedBalance = initialBalances.get("bob").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(betterPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "bob", Asset.QORA, expectedBalance);

			// Bob test asset
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe Qora
			expectedBalance = initialBalances.get("chloe").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "chloe", Asset.QORA, expectedBalance);

			// Chloe test asset
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId).add(tradedTestAssetAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert Qora
			expectedBalance = initialBalances.get("dilbert").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(basePrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORA, expectedBalance);

			// Dilbert test asset
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.testAssetId, expectedBalance);

			// Check orders
			OrderData aliceOrderData = repository.getAssetRepository().fromOrderId(aliceOrderId);
			OrderData bobOrderData = repository.getAssetRepository().fromOrderId(bobOrderId);
			OrderData chloeOrderData = repository.getAssetRepository().fromOrderId(chloeOrderId);
			OrderData dilbertOrderData = repository.getAssetRepository().fromOrderId(dilbertOrderId);

			// Alice's fulfilled
			Common.assertEqualBigDecimals("Alice's order's fulfilled amount incorrect", tradedTestAssetAmount, aliceOrderData.getFulfilled());

			// Bob's fulfilled should be zero
			Common.assertEqualBigDecimals("Bob's order should be totally unfulfilled", BigDecimal.ZERO, bobOrderData.getFulfilled());

			// Chloe's fulfilled
			Common.assertEqualBigDecimals("Chloe's order's fulfilled amount incorrect", tradedTestAssetAmount, chloeOrderData.getFulfilled());

			// Dilbert's fulfilled should be zero
			Common.assertEqualBigDecimals("Dilbert's order should be totally unfulfilled", BigDecimal.ZERO, dilbertOrderData.getFulfilled());
		}
	}

}