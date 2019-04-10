package org.qora.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.qora.asset.Asset;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.AssetUtils;
import org.qora.test.common.Common;

import java.math.BigDecimal;
import java.util.Map;

public class OldTradingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-old-asset.json");
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
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
			// Issue indivisible asset
			asset112Id = AssetUtils.issueAsset(repository, "alice", "RUB.iPLZ", 999999999999L, false);

			// Issue another indivisible asset
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

		AssetUtils.genericTradeTest(asset113Id, asset112Id, asset113Amount, asset112Price, asset112Amount, asset113Price, asset113Amount, asset112Amount, asset112Matched, asset113Matched);

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
			AccountUtils.assertBalance(repository, "alice", asset113Id, expectedBalance);

			// Alice asset 112
			expectedBalance = initialBalances.get("alice").get(asset112Id).add(asset112Matched2);
			AccountUtils.assertBalance(repository, "alice", asset112Id, expectedBalance);

			BigDecimal expectedFulfilled = asset113Matched2;
			BigDecimal actualFulfilled = repository.getAssetRepository().fromOrderId(furtherOrderId).getFulfilled();
			assertEqualBigDecimals("Order fulfilled incorrect", expectedFulfilled, actualFulfilled);
		}
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

		final BigDecimal aliceAmount = new BigDecimal("24.00000000").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("0.08333333").setScale(8);

		final BigDecimal bobAmount = new BigDecimal("2.00000000").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("12.00000000").setScale(8);

		final BigDecimal aliceCommitment = aliceAmount;
		final BigDecimal bobCommitment = bobAmount;

		// Due to rounding these are the expected traded amounts.
		final BigDecimal aliceReturn = new BigDecimal("1.99999992").setScale(8);
		final BigDecimal bobReturn = new BigDecimal("24.00000000").setScale(8);

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

	/**
	 * Check legacy qora1 blockchain matching behaviour.
	 */
	@Test
	public void testQora1Compat() throws DataException {
		// Asset 61 [ATFunding] was issued by QYsLsfwMRBPnunmuWmFkM4hvGsfooY8ssU with 250,000,000 quantity and was divisible.

		// Target order 2jMinWSBjxaLnQvhcEoWGs2JSdX7qbwxMTZenQXXhjGYDHCJDL6EjXPz5VXYuUfZM5LvRNNbcaeBbM6Xhb4tN53g
		// Creator was QZyuTa3ygjThaPRhrCp1BW4R5Sed6uAGN8 at 2014-10-23 11:14:42.525000+0:00
		// Have: 150000 [ATFunding], Price: 1.7000000 QORA

		// Initiating order 3Ufqi52nDL3Gi7KqVXpgebVN5FmLrdq2XyUJ11BwSV4byxQ2z96Q5CQeawGyanhpXS4XkYAaJTrNxsDDDxyxwbMN
		// Creator was QMRoD3RS5vJ4DVNBhBgGtQG4KT3PhkNALH at 2015-03-27 12:24:02.945000+0:00
		// Have: 2 QORA, Price: 0.58 [ATFunding]

		// Trade: 1.17647050 [ATFunding] for 1.99999985 QORA

		// Load/check settings, which potentially sets up blockchain config, etc.
		Common.useSettings("test-settings-old-asset.json");

		// Transfer some test asset to bob
		try (Repository repository = RepositoryManager.getRepository()) {
			AssetUtils.transferAsset(repository, "alice", "bob", AssetUtils.testAssetId, BigDecimal.valueOf(200000L).setScale(8));
		}

		final BigDecimal aliceAmount = new BigDecimal("150000").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("1.70000000").setScale(8);

		final BigDecimal bobAmount = new BigDecimal("2.00000000").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("0.58000000").setScale(8);

		final BigDecimal aliceCommitment = aliceAmount;
		final BigDecimal bobCommitment = bobAmount;

		final BigDecimal aliceReturn = new BigDecimal("1.99999985").setScale(8);
		final BigDecimal bobReturn = new BigDecimal("1.17647050").setScale(8);

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, Asset.QORA, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

	/**
	 * Check legacy qora1 blockchain matching behaviour.
	 */
	@Test
	public void testQora1Compat2() throws DataException {
		// Asset 95 [Bitcoin] was issued by QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj with 21000000 quantity and was divisible.
		// Asset 96 [BitBTC] was issued by QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj with 21000000 quantity and was divisible.

		// Target order 3jinKPHEak9xrjeYtCaE1PawwRZeRkhYA6q4A7sqej7f3jio8WwXwXpfLWVZkPQ3h6cVdwPhcDFNgbbrBXcipHee
		// Creator was QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj at 2015-06-10 20:31:44.840000+0:00
		// Have: 1000000 [BitBTC], Price: 0.90000000 [Bitcoin]

		// Initiating order Jw1UfgspZ344waF8qLhGJanJXVa32FBoVvMW5ByFkyHvZEumF4fPqbaGMa76ba1imC4WX5t3Roa7r23Ys6rhKAA
		// Creator was QiGx93L9rNHSNWCY1bJnQTPwB3nhxYTCUj at 2015-06-14 17:49:41.410000+0:00
		// Have: 73251 [Bitcoin], Price: 1.01 [BitBTC]

		// Trade: 81389.99991860 [BitBTC] for 73250.99992674 [Bitcoin]

		// Load/check settings, which potentially sets up blockchain config, etc.
		Common.useSettings("test-settings-old-asset.json");

		// Transfer some test asset to bob
		try (Repository repository = RepositoryManager.getRepository()) {
			AssetUtils.transferAsset(repository, "alice", "bob", AssetUtils.testAssetId, BigDecimal.valueOf(200000L).setScale(8));
		}

		final BigDecimal aliceAmount = new BigDecimal("1000000").setScale(8);
		final BigDecimal alicePrice = new BigDecimal("0.90000000").setScale(8);

		final BigDecimal bobAmount = new BigDecimal("73251").setScale(8);
		final BigDecimal bobPrice = new BigDecimal("1.01000000").setScale(8);

		final BigDecimal aliceCommitment = aliceAmount;
		final BigDecimal bobCommitment = bobAmount;

		final BigDecimal aliceReturn = new BigDecimal("73250.99992674").setScale(8);
		final BigDecimal bobReturn = new BigDecimal("81389.99991860").setScale(8);

		AssetUtils.genericTradeTest(Asset.QORA, AssetUtils.testAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn);
	}

}