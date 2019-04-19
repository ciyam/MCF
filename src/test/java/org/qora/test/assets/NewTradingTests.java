package org.qora.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
		final BigDecimal goldAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal price = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal otherAmount = BigDecimal.valueOf(48L).setScale(8);

		// amounts are in GOLD
		// prices are in OTHER/GOLD

		final BigDecimal aliceAmount = goldAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = goldAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = goldAmount;
		final BigDecimal bobCommitment = otherAmount;

		final BigDecimal aliceReturn = otherAmount;
		final BigDecimal bobReturn = goldAmount;

		// alice (target) order: have 'goldAmount' GOLD, want OTHER @ 'price' OTHER/GOLD (commits goldAmount GOLD)
		// bob (initiating) order: have OTHER, want 'goldAmount' GOLD @ 'price' OTHER/GOLD (commits goldAmount*price = otherAmount OTHER)
		// Alice should be -goldAmount, +otherAmount
		// Bob should be -otherAmount, +goldAmount

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	@Test
	public void testSimpleInverted() throws DataException {
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

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching using divisible and indivisible assets.
	 */
	@Test
	public void testMixedDivisibility() throws DataException {
		// Issue indivisible asset
		long indivAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			indivAssetId = AssetUtils.issueAsset(repository, "alice", "INDIV", 100000000L, false);
		}

		final BigDecimal indivAmount = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal otherAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal price = BigDecimal.valueOf(12L).setScale(8);

		// amounts are in INDIV
		// prices are in OTHER/INDIV

		final BigDecimal aliceAmount = indivAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = indivAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = indivAmount;
		final BigDecimal bobCommitment = otherAmount;

		final BigDecimal aliceReturn = otherAmount;
		final BigDecimal bobReturn = indivAmount;

		AssetUtils.genericTradeTest(indivAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching using divisible and indivisible assets.
	 */
	@Test
	public void testMixedDivisibilityInverted() throws DataException {
		// Issue indivisible asset
		long indivAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			indivAssetId = AssetUtils.issueAsset(repository, "bob", "INDIV", 100000000L, false);
		}

		final BigDecimal indivAmount = BigDecimal.valueOf(2L).setScale(8);
		final BigDecimal testAmount = BigDecimal.valueOf(24L).setScale(8);
		final BigDecimal price = BigDecimal.valueOf(12L).setScale(8);

		// amounts are in INDIV
		// prices are in TEST/INDIV

		final BigDecimal aliceAmount = indivAmount;
		final BigDecimal alicePrice = price;

		final BigDecimal bobAmount = indivAmount;
		final BigDecimal bobPrice = price;

		final BigDecimal aliceCommitment = testAmount;
		final BigDecimal bobCommitment = indivAmount;

		final BigDecimal aliceReturn = indivAmount;
		final BigDecimal bobReturn = testAmount;

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, indivAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
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
		final BigDecimal aliceAmount = new BigDecimal("24.00000000").setScale(8); // OTHER
		final BigDecimal alicePrice = new BigDecimal("0.08333333").setScale(8); // TEST/OTHER
		final BigDecimal aliceCommitment = new BigDecimal("1.99999992").setScale(8); // 24 * 0.08333333 = 1.99999992 TEST

		final BigDecimal bobAmount = new BigDecimal("24.00000000").setScale(8); // OTHER
		final BigDecimal bobPrice = new BigDecimal("0.08333333").setScale(8); // TEST/OTHER
		final BigDecimal bobCommitment = new BigDecimal("24.00000000").setScale(8); // OTHER

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("24.00000000").setScale(8); // OTHER
		final BigDecimal bobReturn = new BigDecimal("1.99999992").setScale(8); // TEST

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovement() throws DataException {
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

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovementInverted() throws DataException {
		// Alice is seller GOLD
		final BigDecimal aliceAmount = new BigDecimal("100").setScale(8); // GOLD
		final BigDecimal alicePrice = new BigDecimal("2").setScale(8); // OTHER/GOLD
		final BigDecimal aliceCommitment = new BigDecimal("100").setScale(8); // GOLD

		// Bob is buying GOLD
		final BigDecimal bobAmount = new BigDecimal("50").setScale(8); // GOLD
		final BigDecimal bobPrice = new BigDecimal("3").setScale(8); // OTHER/GOLD
		final BigDecimal bobCommitment = new BigDecimal("150").setScale(8); // 50 * 3 = 150 OTHER

		// Expected traded amounts
		final BigDecimal aliceReturn = new BigDecimal("100").setScale(8); // 50 * 2 = 100 OTHER
		final BigDecimal bobReturn = new BigDecimal("50").setScale(8); // 50 GOLD
		final BigDecimal bobSaving = new BigDecimal("50").setScale(8); // 50 * (3 - 2) = 50 OTHER

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovement() throws DataException {
		// Amounts are in GOLD
		// Prices are in OTHER/GOLD

		final BigDecimal initialGoldAssetAmount = new BigDecimal("24.00000000").setScale(8);

		final BigDecimal basePrice = new BigDecimal("1.00000000").setScale(8);
		final BigDecimal betterPrice = new BigDecimal("2.10000000").setScale(8);
		final BigDecimal bestPrice = new BigDecimal("2.40000000").setScale(8);

		final BigDecimal minimalPrice = new BigDecimal("1.5000000").setScale(8);
		final BigDecimal matchingGoldAssetAmount = new BigDecimal("12.00000000").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			// Give some OTHER to Chloe and Dilbert
			AssetUtils.transferAsset(repository, "bob", "chloe", AssetUtils.otherAssetId, BigDecimal.valueOf(1000L).setScale(8));
			AssetUtils.transferAsset(repository, "bob", "dilbert", AssetUtils.otherAssetId, BigDecimal.valueOf(1000L).setScale(8));

			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.otherAssetId, AssetUtils.goldAssetId);

			// Create 'better' initial order: buying GOLD @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, bestPrice);

			// Create 'base' initial order: buying GOLD @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, basePrice);

			// Create matching order: selling GOLD @ minimalPrice which would match at least one buy order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, matchingGoldAssetAmount, minimalPrice);

			// Check balances to check expected outcome
			BigDecimal expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			BigDecimal matchedOtherAmount = matchingGoldAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN);
			BigDecimal tradedGoldAssetAmount = matchingGoldAssetAmount;
			// NO refund due to price improvement - Alice receives more OTHER back than she was expecting
			BigDecimal aliceSaving = BigDecimal.ZERO;

			// Alice GOLD
			BigDecimal aliceCommitment = matchingGoldAssetAmount;
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId).subtract(aliceCommitment).add(aliceSaving);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			// Alice OTHER
			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(matchedOtherAmount);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob OTHER
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(initialGoldAssetAmount.multiply(betterPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			// Bob GOLD
			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);

			// Chloe OTHER
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.otherAssetId).subtract(initialGoldAssetAmount.multiply(bestPrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.otherAssetId, expectedBalance);

			// Chloe GOLD
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.goldAssetId).add(tradedGoldAssetAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.goldAssetId, expectedBalance);

			// Dilbert OTHER
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.otherAssetId).subtract(initialGoldAssetAmount.multiply(basePrice).setScale(8, RoundingMode.DOWN));
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.otherAssetId, expectedBalance);

			// Dilbert GOLD
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.goldAssetId);
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.goldAssetId, expectedBalance);

			// Check orders
			OrderData aliceOrderData = repository.getAssetRepository().fromOrderId(aliceOrderId);
			OrderData bobOrderData = repository.getAssetRepository().fromOrderId(bobOrderId);
			OrderData chloeOrderData = repository.getAssetRepository().fromOrderId(chloeOrderId);
			OrderData dilbertOrderData = repository.getAssetRepository().fromOrderId(dilbertOrderId);

			// Alice's fulfilled
			Common.assertEqualBigDecimals("Alice's order's fulfilled amount incorrect", tradedGoldAssetAmount, aliceOrderData.getFulfilled());

			// Bob's fulfilled should be zero
			Common.assertEqualBigDecimals("Bob's order should be totally unfulfilled", BigDecimal.ZERO, bobOrderData.getFulfilled());

			// Chloe's fulfilled
			Common.assertEqualBigDecimals("Chloe's order's fulfilled amount incorrect", tradedGoldAssetAmount, chloeOrderData.getFulfilled());

			// Dilbert's fulfilled should be zero
			Common.assertEqualBigDecimals("Dilbert's order should be totally unfulfilled", BigDecimal.ZERO, dilbertOrderData.getFulfilled());
		}
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovementInverted() throws DataException {
		// Amounts are in OTHER
		// Prices are in TEST/OTHER

		final BigDecimal initialOtherAmount = new BigDecimal("24.00000000").setScale(8);

		final BigDecimal basePrice = new BigDecimal("3.00000000").setScale(8);
		final BigDecimal betterPrice = new BigDecimal("2.10000000").setScale(8);
		final BigDecimal bestPrice = new BigDecimal("1.40000000").setScale(8);

		final BigDecimal maximalPrice = new BigDecimal("2.5000000").setScale(8);
		final BigDecimal aliceOtherAmount = new BigDecimal("12.00000000").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			// Give some OTHER to Chloe and Dilbert
			AssetUtils.transferAsset(repository, "bob", "chloe", AssetUtils.otherAssetId, BigDecimal.valueOf(1000L).setScale(8));
			AssetUtils.transferAsset(repository, "bob", "dilbert", AssetUtils.otherAssetId, BigDecimal.valueOf(1000L).setScale(8));

			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			// Create 'better' initial order: selling OTHER @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, bestPrice);

			// Create 'base' initial order: selling OTHER @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, basePrice);

			// Create matching order: buying OTHER @ maximalPrice which would match at least one sell order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceOtherAmount, maximalPrice);

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
			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(matchedOtherAmount);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob OTHER
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			// Bob TEST
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId); // no trade
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe OTHER
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.otherAssetId, expectedBalance);

			// Chloe TEST
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId).add(tradedTestAmount);
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert OTHER
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.otherAssetId).subtract(initialOtherAmount);
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.otherAssetId, expectedBalance);

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
		// amounts are in GOLD
		// prices are in OTHER/GOLD

		// Selling 10 GOLD @ 2 OTHER/GOLD min so wants 20 OTHER minimum
		final BigDecimal aliceAmount = new BigDecimal("10").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("2").setScale(8);

		// Buying 10 GOLD @ 1 OTHER/GOLD max, paying 10 OTHER maximum
		final BigDecimal bobAmount = new BigDecimal("10").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("1").setScale(8);

		final BigDecimal aliceCommitment = new BigDecimal("10").setScale(8); // 10 GOLD
		final BigDecimal bobCommitment = new BigDecimal("10").setScale(8); // 10 GOLD * 1 OTHER/GOLD = 10 OTHER

		// Orders should not match!
		final BigDecimal aliceReturn = BigDecimal.ZERO;
		final BigDecimal bobReturn = BigDecimal.ZERO;

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

	/**
	 * Check that orders don't match.
	 * <p>
	 * "target" order with want-asset = amount-asset
	 */
	@Test
	public void testWorsePriceNoMatchInverted() throws DataException {
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

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, BigDecimal.ZERO);
	}

}