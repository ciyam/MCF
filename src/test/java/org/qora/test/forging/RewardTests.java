package org.qora.test.forging;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.block.BlockChain.RewardsByHeight;
import org.qora.block.BlockGenerator;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.Common;

public class RewardTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimpleReward() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA);

			PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, "alice");

			BigDecimal firstReward = BlockChain.getInstance().getBlockRewardsByHeight().get(0).reward;

			BlockGenerator.generateTestingBlock(repository, forgingAccount);

			BigDecimal expectedBalance = initialBalances.get("alice").get(Asset.QORA).add(firstReward);
			AccountUtils.assertBalance(repository, "alice", Asset.QORA, expectedBalance);
		}
	}

	@Test
	public void testRewards() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, BigDecimal>> initialBalances = AccountUtils.getBalances(repository, Asset.QORA);

			PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, "alice");

			List<RewardsByHeight> rewards = BlockChain.getInstance().getBlockRewardsByHeight();

			int rewardIndex = rewards.size() - 1;

			RewardsByHeight rewardInfo = rewards.get(rewardIndex);
			BigDecimal expectedBalance = initialBalances.get("alice").get(Asset.QORA);

			for (int height = rewardInfo.height; height > 1; --height) {
				if (height < rewardInfo.height) {
					--rewardIndex;
					rewardInfo = rewards.get(rewardIndex);
				}

				BlockGenerator.generateTestingBlock(repository, forgingAccount);
				expectedBalance = expectedBalance.add(rewardInfo.reward);
			}

			AccountUtils.assertBalance(repository, "alice", Asset.QORA, expectedBalance);
		}
	}

}
