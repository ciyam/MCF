package org.qora.test.assets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.AssetUtils;
import org.qora.test.common.Common;

public class CancellingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimpleCancel() throws DataException {
		BigDecimal amount = new BigDecimal("1234.87654321").setScale(8);
		BigDecimal price = new BigDecimal("1.35615263").setScale(8);

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, amount, price);
			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);

			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, amount, price);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances match pre-ordering values
			BigDecimal expectedBalance;

			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialTargetMatchCancel() throws DataException {
		BigDecimal aliceAmount = new BigDecimal("1234").setScale(8); // OTHER
		BigDecimal alicePrice = new BigDecimal("1.5").setScale(8); // TEST/OTHER

		BigDecimal bobAmount = new BigDecimal("500").setScale(8); // OTHER
		BigDecimal bobPrice = new BigDecimal("1.2").setScale(8); // TEST/OTHER

		BigDecimal aliceCommitment = aliceAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // TEST
		BigDecimal bobCommitment = bobAmount; // OTHER

		BigDecimal matchedAmount = aliceAmount.min(bobAmount); // 500 OTHER

		BigDecimal aliceReturn = matchedAmount; // OTHER
		BigDecimal bobReturn = matchedAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // TEST

		BigDecimal aliceRefund = aliceAmount.subtract(matchedAmount).multiply(alicePrice).setScale(8, RoundingMode.DOWN); // TEST
		BigDecimal bobRefund = BigDecimal.ZERO; // because Bob's order is fully matched

		BigDecimal bobSaving = BigDecimal.ZERO; // not in this direction 

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			BigDecimal expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(aliceCommitment).add(aliceRefund);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(aliceReturn);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(bobCommitment).add(bobSaving).add(bobRefund);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId).add(bobReturn);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialInitiatorMatchCancel() throws DataException {
		BigDecimal aliceAmount = new BigDecimal("500").setScale(8); // OTHER
		BigDecimal alicePrice = new BigDecimal("1.5").setScale(8); // TEST/OTHER

		BigDecimal bobAmount = new BigDecimal("1234").setScale(8); // OTHER
		BigDecimal bobPrice = new BigDecimal("1.2").setScale(8); // TEST/OTHER

		BigDecimal aliceCommitment = aliceAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // TEST
		BigDecimal bobCommitment = bobAmount; // OTHER

		BigDecimal matchedAmount = aliceAmount.min(bobAmount); // 500 OTHER

		BigDecimal aliceReturn = matchedAmount; // OTHER
		BigDecimal bobReturn = matchedAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // TEST

		BigDecimal aliceRefund = BigDecimal.ZERO; // because Alice's order is fully matched
		BigDecimal bobRefund = bobAmount.subtract(matchedAmount); // OTHER

		BigDecimal bobSaving = BigDecimal.ZERO; // not in this direction 

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			BigDecimal expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId).subtract(aliceCommitment).add(aliceRefund);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(aliceReturn);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(bobCommitment).add(bobSaving).add(bobRefund);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId).add(bobReturn);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialTargetMatchCancelInverted() throws DataException {
		BigDecimal aliceAmount = new BigDecimal("1234").setScale(8); // GOLD
		BigDecimal alicePrice = new BigDecimal("1.2").setScale(8); // OTHER/GOLD

		BigDecimal bobAmount = new BigDecimal("500").setScale(8); // GOLD
		BigDecimal bobPrice = new BigDecimal("1.5").setScale(8); // OTHER/GOLD

		BigDecimal aliceCommitment = aliceAmount; // GOLD
		BigDecimal bobCommitment = bobAmount.multiply(bobPrice).setScale(8, RoundingMode.DOWN); // OTHER

		BigDecimal matchedAmount = aliceAmount.min(bobAmount); // 500 GOLD

		BigDecimal aliceReturn = matchedAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // OTHER
		BigDecimal bobReturn = matchedAmount; // GOLD

		BigDecimal aliceRefund = aliceAmount.subtract(matchedAmount); // GOLD
		BigDecimal bobRefund = BigDecimal.ZERO; // because Bob's order is fully matched

		BigDecimal bobSaving = new BigDecimal("150").setScale(8); // (1.5 - 1.2) * 500 = 150 OTHER 

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.goldAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			BigDecimal expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId).subtract(aliceCommitment).add(aliceRefund);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(aliceReturn);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(bobCommitment).add(bobSaving).add(bobRefund);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId).add(bobReturn);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialInitiatorMatchCancelInverted() throws DataException {
		BigDecimal aliceAmount = new BigDecimal("500").setScale(8); // GOLD
		BigDecimal alicePrice = new BigDecimal("1.2").setScale(8); // OTHER/GOLD

		BigDecimal bobAmount = new BigDecimal("1234").setScale(8); // GOLD
		BigDecimal bobPrice = new BigDecimal("1.5").setScale(8); // OTHER/GOLD

		BigDecimal aliceCommitment = aliceAmount; // GOLD
		BigDecimal bobCommitment = bobAmount.multiply(bobPrice).setScale(8, RoundingMode.DOWN); // OTHER

		BigDecimal matchedAmount = aliceAmount.min(bobAmount); // 500 GOLD

		BigDecimal aliceReturn = matchedAmount.multiply(alicePrice).setScale(8, RoundingMode.DOWN); // OTHER
		BigDecimal bobReturn = matchedAmount; // GOLD

		BigDecimal aliceRefund = BigDecimal.ZERO; // because Alice's order is fully matched
		BigDecimal bobRefund = bobAmount.subtract(matchedAmount).multiply(bobPrice).setScale(8, RoundingMode.DOWN); // OTHER

		BigDecimal bobSaving = new BigDecimal("150").setScale(8); // (1.5 - 1.2) * 500 = 150 OTHER 

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.goldAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			BigDecimal expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId).subtract(aliceCommitment).add(aliceRefund);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId).add(aliceReturn);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId).subtract(bobCommitment).add(bobSaving).add(bobRefund);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId).add(bobReturn);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);
		}
	}

}
