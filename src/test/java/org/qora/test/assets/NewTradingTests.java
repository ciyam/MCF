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

		// amounts are in TEST
		// prices are in QORA/TEST

		final BigDecimal aliceAmount = testAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = testAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = testAmount;
		final BigDecimal bobCommitment = qoraAmount;

		final BigDecimal aliceReturn = qoraAmount;
		final BigDecimal bobReturn = testAmount;

		// alice (target) order: have 'testAmount' TEST, want QORA @ 'price' QORA/TEST (commits testAmount TEST)
		// bob (initiating) order: have QORA, want 'testAmount' TEST @ 'price' QORA/TEST (commits testAmount*price = qoraAmount QORA)
		// Alice should be -testAmount, +qoraAmount
		// Bob should be -qoraAmount, +testAmount

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	@Test
	public void testSimpleInverted() throws DataException {
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "OTHER", 100000000L, false);
		}

		final BigDecimal testAmount = BigDecimal.valueOf(48L).setScale(8);
		final BigDecimal price = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal otherAmount = BigDecimal.valueOf(24L).setScale(8);

		// amounts are in OTHER
		// prices are in TEST/OTHER

		final BigDecimal aliceAmount = otherAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = otherAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = testAmount;
		final BigDecimal bobCommitment = otherAmount;

		final BigDecimal aliceReturn = otherAmount;
		final BigDecimal bobReturn = testAmount;

		// alice (target) order: have TEST, want 'otherAmount' OTHER @ 'price' TEST/OTHER (commits otherAmount*price = testAmount TEST)
		// bob (initiating) order: have 'otherAmount' OTHER, want TEST @ 'price' TEST/OTHER (commits otherAmount OTHER)
		// Alice should be -testAmount, +otherAmount
		// Bob should be -otherAmount, +testAmount

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
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
	 * Alice's price is 12 QORA per OTHER so the OTHER per QORA unit price is 0.08333333...<br>
	 * Bob wants to spend 24 QORA so:
	 * <p>
	 * <ol>
	 * <li> 24 QORA * (1 / 0.0833333...) = 1.99999999 OTHER </li>
	 * <li> 24 QORA / 0.08333333.... = 2 OTHER </li>
	 * </ol>
	 * The second result is obviously more intuitive as is critical where assets are not divisible,
	 * like OTHER in this test case.
	 * <p>
	 * @see NewTradingTests#testOldNonExactFraction
	 * @see NewTradingTests#testNonExactFraction
	 * @throws DataException
	 */
	@Test
	public void testMixedDivisibility() throws DataException {
		// Issue indivisible asset
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			otherAssetId = AssetUtils.issueAsset(repository, "alice", "OTHER", 100000000L, false);
		}

		final BigDecimal otherAmount = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal qoraAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal price = qoraAmount.divide(otherAmount, RoundingMode.DOWN);

		// amounts are in OTHER
		// prices are in QORA/OTHER

		final BigDecimal aliceAmount = otherAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = otherAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = otherAmount;
		final BigDecimal bobCommitment = qoraAmount;

		final BigDecimal aliceReturn = qoraAmount;
		final BigDecimal bobReturn = otherAmount;

		AssetUtils.genericTradeTest(otherAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
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

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
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

		// Buying 3 riches @ 1 rags/riches max, so expecting to pay 3 rags max
		final BigDecimal aliceAmount = new BigDecimal("3").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("1").setScale(8);
		final BigDecimal aliceCommitment = aliceAmount.multiply(alicePrice).setScale(8); // rags

		// Selling 8 riches @ 0.25 rags/riches min, so expecting 2 rags min
		final BigDecimal bobAmount = new BigDecimal("8").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("0.25").setScale(8);
		final BigDecimal bobCommitment = bobAmount; // riches

		final BigDecimal aliceReturn = aliceAmount; // riches
		final BigDecimal bobReturn = aliceAmount.multiply(alicePrice).setScale(8);

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
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
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "OTHER", 5000L, true);
		}

		final BigDecimal aliceAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("0.08333333").setScale(8);
		final BigDecimal aliceCommitment = new BigDecimal("1.99999992").setScale(8);

		final BigDecimal bobAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("0.08333333").setScale(8);
		final BigDecimal bobCommitment = new BigDecimal("24.00000000").setScale(8);

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("24.00000000").setScale(8); // OTHER
		final BigDecimal bobReturn = new BigDecimal("1.99999992").setScale(8); // TEST

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovement() throws DataException {
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "OTHER", 5000L, true);
		}

		// Alice is buying OTHER
		final BigDecimal aliceAmount = new BigDecimal("100").setScale(8); // OTHER
		final BigDecimal alicePrice = new BigDecimal("0.3").setScale(8); // TEST/OTHER
		final BigDecimal aliceCommitment = new BigDecimal("30").setScale(8); // 100 * 0.3 = 30 TEST

		// Bob is selling OTHER
		final BigDecimal bobAmount = new BigDecimal("100").setScale(8); // OTHER
		final BigDecimal bobPrice = new BigDecimal("0.2").setScale(8); // TEST/OTHER
		final BigDecimal bobCommitment = new BigDecimal("100").setScale(8); // OTHER

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("100").setScale(8); // OTHER
		final BigDecimal bobReturn = new BigDecimal("30").setScale(8); // TEST

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovementInverted() throws DataException {
		// Alice is seller TEST
		final BigDecimal aliceAmount = new BigDecimal("100").setScale(8); // TEST
		final BigDecimal alicePrice = new BigDecimal("2").setScale(8); // QORA/TEST
		final BigDecimal aliceCommitment = new BigDecimal("100").setScale(8); // TEST

		// Bob is buying TEST
		final BigDecimal bobAmount = new BigDecimal("50").setScale(8); // TEST
		final BigDecimal bobPrice = new BigDecimal("3").setScale(8); // QORA/TEST
		final BigDecimal bobCommitment = new BigDecimal("150").setScale(8); // 50 * 3 = 150 QORA

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("100").setScale(8); // 50 * 2 = 100 QORA
		final BigDecimal bobReturn = new BigDecimal("50").setScale(8); // 50 TEST
		final BigDecimal bobSaving = new BigDecimal("50").setScale(8); // 50 * (3 - 2) = 50 QORA

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovement() throws DataException {
		// Amounts are in TEST
		// Prices are in QORA/TEST

		final BigDecimal initialTestAssetAmount = new BigDecimal("24.00000000").setScale(8);

		final BigDecimal basePrice = new BigDecimal("1.00000000").setScale(8);
		final BigDecimal betterPrice = new BigDecimal("2.10000000").setScale(8);
		final BigDecimal bestPrice = new BigDecimal("2.40000000").setScale(8);

		final BigDecimal minimalPrice = new BigDecimal("1.5000000").setScale(8);
		final BigDecimal matchingTestAssetAmount = new BigDecimal("12.00000000").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA, AssetUtils.testAssetId);

			// Create 'better' initial order: buying TEST @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, bestPrice);

			// Create 'base' initial order: buying TEST @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", Asset.QORA, AssetUtils.testAssetId, initialTestAssetAmount, basePrice);

			// Create matching order: selling TEST @ minimalPrice which would match at least one buy order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, Asset.QORA, matchingTestAssetAmount, minimalPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			BigDecimal matchedQoraAmount = matchingTestAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN);
			BigDecimal tradedTestAssetAmount = matchingTestAssetAmount;
			// NO refund due to price improvement - Alice receives more QORA back than she was expecting
			BigDecimal aliceSaving = BigDecimal.ZERO;

			// Alice TEST
			BigDecimal aliceCommitment = matchingTestAssetAmount;
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(aliceCommitment).add(aliceSaving);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			// Alice QORA
			expectedBalance = initialBalances.get("alice").get(Asset.QORA).add(matchedQoraAmount);
			AccountUtils.assertBalance(repository, "alice", Asset.QORA, expectedBalance);

			// Bob QORA
			expectedBalance = initialBalances.get("bob").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(betterPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "bob", Asset.QORA, expectedBalance);

			// Bob TEST
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe QORA
			expectedBalance = initialBalances.get("chloe").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "chloe", Asset.QORA, expectedBalance);

			// Chloe TEST
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId).add(tradedTestAssetAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert QORA
			expectedBalance = initialBalances.get("dilbert").get(Asset.QORA).subtract(initialTestAssetAmount.multiply(basePrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORA, expectedBalance);

			// Dilbert TEST
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

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovementInverted() throws DataException {
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "OTHER", 100000000L, true);

			AssetUtils.transferAsset(repository, "bob", "chloe", otherAssetId, BigDecimal.valueOf(1000L).setScale(8));

			AssetUtils.transferAsset(repository, "bob", "dilbert", otherAssetId, BigDecimal.valueOf(1000L).setScale(8));
		}

		// Amounts are in OTHER
		// Prices are in TEST/OTHER

		final BigDecimal initialOtherAmount = new BigDecimal("24.00000000").setScale(8);

		final BigDecimal basePrice = new BigDecimal("3.00000000").setScale(8);
		final BigDecimal betterPrice = new BigDecimal("2.10000000").setScale(8);
		final BigDecimal bestPrice = new BigDecimal("1.40000000").setScale(8);

		final BigDecimal maximalPrice = new BigDecimal("2.5000000").setScale(8);
		final BigDecimal aliceOtherAmount = new BigDecimal("12.00000000").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA, AssetUtils.testAssetId, otherAssetId);

			// Create 'better' initial order: selling OTHER @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", otherAssetId, AssetUtils.testAssetId, initialOtherAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", otherAssetId, AssetUtils.testAssetId, initialOtherAmount, bestPrice);

			// Create 'base' initial order: selling OTHER @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", otherAssetId, AssetUtils.testAssetId, initialOtherAmount, basePrice);

			// Create matching order: buying OTHER @ maximalPrice which would match at least one sell order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, otherAssetId, aliceOtherAmount, maximalPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			BigDecimal matchedOtherAmount = aliceOtherAmount;
			BigDecimal tradedTestAmount = aliceOtherAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN);
			// Due to price improvement, Alice should get back some of her TEST
			BigDecimal aliceSaving = maximalPrice.subtract(bestPrice).abs().multiply(matchedOtherAmount).setScale(8, RoundingMode.DOWN);

			// Alice TEST
			BigDecimal aliceCommitment = aliceOtherAmount.multiply(maximalPrice).setScale(8, RoundingMode.DOWN);
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(aliceCommitment).add(aliceSaving);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			// Alice OTHER
			expectedBalance = initialBalances.get("alice").get(otherAssetId).add(matchedOtherAmount);
			AccountUtils.assertBalance(repository, "alice", otherAssetId, expectedBalance);

			// Bob OTHER
			expectedBalance = initialBalances.get("bob").get(otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "bob", otherAssetId, expectedBalance);

			// Bob TEST
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId); // no trade
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe OTHER
			expectedBalance = initialBalances.get("chloe").get(otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "chloe", otherAssetId, expectedBalance);

			// Chloe TEST
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId).add(tradedTestAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert OTHER
			expectedBalance = initialBalances.get("dilbert").get(otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "dilbert", otherAssetId, expectedBalance);

			// Dilbert TEST
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.testAssetId); // no trade
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.testAssetId, expectedBalance);

			// Check orders
			OrderData aliceOrderData = repository.getAssetRepository().fromOrderId(aliceOrderId);
			OrderData bobOrderData = repository.getAssetRepository().fromOrderId(bobOrderId);
			OrderData chloeOrderData = repository.getAssetRepository().fromOrderId(chloeOrderId);
			OrderData dilbertOrderData = repository.getAssetRepository().fromOrderId(dilbertOrderId);

			// Alice's fulfilled
			Common.assertEqualBigDecimals("Alice's order's fulfilled amount incorrect", matchedOtherAmount, aliceOrderData.getFulfilled());

			// Bob's fulfilled should be zero
			Common.assertEqualBigDecimals("Bob's order should be totally unfulfilled", BigDecimal.ZERO, bobOrderData.getFulfilled());

			// Chloe's fulfilled
			Common.assertEqualBigDecimals("Chloe's order's fulfilled amount incorrect", matchedOtherAmount, chloeOrderData.getFulfilled());

			// Dilbert's fulfilled should be zero
			Common.assertEqualBigDecimals("Dilbert's order should be totally unfulfilled", BigDecimal.ZERO, dilbertOrderData.getFulfilled());
		}
	}

	/**
	 * Check that orders don't match.
	 * <p>
	 * "target" order with have-asset = amount-asset
	 */
	@Test
	public void testWorsePriceNoMatch() throws DataException {
		// amounts are in TEST
		// prices are in QORA/TEST

		// Selling 10 TEST @ 2 QORA/TEST min so wants 20 QORA minimum
		final BigDecimal aliceAmount = new BigDecimal("10").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("2").setScale(8);

		// Buying 10 TEST @ 1 QORA/TEST max, paying 10 QORA maximum
		final BigDecimal bobAmount = new BigDecimal("10").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("1").setScale(8);

		final BigDecimal aliceCommitment = new BigDecimal("10").setScale(8); // 10 TEST
		final BigDecimal bobCommitment = new BigDecimal("10").setScale(8); // 10 TEST * 1 QORA/TEST = 10 QORA

		// Orders should not match!
		final BigDecimal aliceReturn = BigDecimal.ZERO;
		final BigDecimal bobReturn = BigDecimal.ZERO;

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check that orders don't match.
	 * <p>
	 * "target" order with want-asset = amount-asset
	 */
	@Test
	public void testWorsePriceNoMatchInverted() throws DataException {
		long otherAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			otherAssetId = AssetUtils.issueAsset(repository, "bob", "OTHER", 100000000L, true);
		}

		// amounts are in OTHER
		// prices are in TEST/OTHER

		// Buying 10 OTHER @ 1 TEST/OTHER max, paying 10 TEST maximum
		final BigDecimal aliceAmount = new BigDecimal("10").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("1").setScale(8);

		// Selling 10 OTHER @ 2 TEST/OTHER min, so wants 20 TEST minimum
		final BigDecimal bobAmount = new BigDecimal("10").setScale(8); // OTHER
		final BigDecimal bobPrice = new BigDecimal("2").setScale(8);

		final BigDecimal aliceCommitment = new BigDecimal("10").setScale(8); // 10 OTHER * 1 TEST/OTHER = 10 TEST
		final BigDecimal bobCommitment = new BigDecimal("10").setScale(8); // 10 OTHER

		// Orders should not match!
		final BigDecimal aliceReturn = BigDecimal.ZERO;
		final BigDecimal bobReturn = BigDecimal.ZERO;

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

}