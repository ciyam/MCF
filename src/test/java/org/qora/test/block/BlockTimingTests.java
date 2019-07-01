package org.qora.test.block;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.block.BlockChain.BlockTimingByHeight;
import org.qora.block.BlockGenerator;
import org.qora.data.block.BlockData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transform.Transformer;

public class BlockTimingTests extends Common {

	private static final BigInteger MAX_DISTANCE;
	static {
		byte[] maxValue = new byte[Transformer.PUBLIC_KEY_LENGTH];
		Arrays.fill(maxValue, (byte) 0xFF);
		MAX_DISTANCE = new BigInteger(1, maxValue);
	}

	private Repository repository;
	private PrivateKeyAccount aliceAccount;

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-v2-subsets.json");
		repository = RepositoryManager.getRepository();
		aliceAccount = Common.getTestAccount(repository, "alice");
	}

	@After
	public void afterTest() throws DataException {
		repository.close();
		repository = null;
	}

	@Test
	public void testBlockIntervals() throws DataException {
		final int numberRounds = 10000;
		final int newBlockTimingHeight = BlockChain.getInstance().getBlockTimingByHeight(1_000_000).height;

		Random random = new Random();
		byte[] randomKey = new byte[32];

		// We need to generate enough blocks to trigger 'new' code...
		for (int height = 2; height < newBlockTimingHeight; ++height)
			BlockGenerator.generateTestingBlock(repository, aliceAccount);

		final BlockData previousBlockData = repository.getBlockRepository().getLastBlock();

		long cumulativeTime = 0;
		for (int i = 0; i < numberRounds; ++i) {
			random.nextBytes(randomKey);
			cumulativeTime += Block.calcMinimumTimestamp(previousBlockData, randomKey);
		}

		cumulativeTime -= previousBlockData.getTimestamp() * numberRounds;
		final long meanTime = cumulativeTime / numberRounds;

		// We're looking for between min/max block times, within a margin
		final int marginPct = 34; // tolerance margin (percent)

		BlockTimingByHeight blockTiming = BlockChain.getInstance().getBlockTimingByHeight(newBlockTimingHeight);

		final long targetBlockTime = blockTiming.target;

		System.out.println(String.format("mean block period with only one generator: %d", meanTime));

		assertTrue(Math.abs(meanTime - targetBlockTime) < (targetBlockTime * marginPct / 100));
	}

	@Test
	public void testContendedBlockIntervals() throws DataException {
		final int numberRounds = 20000;
		final int numberContestants = 25;
		final int newBlockTimingHeight = BlockChain.getInstance().getBlockTimingByHeight(1_000_000).height;

		Random random = new Random();
		byte[] randomKey = new byte[32];

		// We need to generate enough blocks to trigger 'new' code...
		for (int height = 2; height < newBlockTimingHeight; ++height)
			BlockGenerator.generateTestingBlock(repository, aliceAccount);

		final BlockData previousBlockData = repository.getBlockRepository().getLastBlock();

		long cumulativeTime = 0;
		for (int ri = 0; ri < numberRounds; ++ri) {
			long lowestTime = 0;

			for (int ci = 0; ci < numberContestants; ++ci) {
				random.nextBytes(randomKey);
				long timestamp = Block.calcMinimumTimestamp(previousBlockData, randomKey);

				if (ci == 0 || timestamp < lowestTime)
					lowestTime = timestamp;
			}

			cumulativeTime += lowestTime;
		}

		cumulativeTime -= previousBlockData.getTimestamp() * numberRounds;
		final long meanTime = cumulativeTime / numberRounds;

		// We're looking for between min/max block times, within a margin
		final int marginPct = 34; // tolerance margin (percent)

		BlockTimingByHeight blockTiming = BlockChain.getInstance().getBlockTimingByHeight(newBlockTimingHeight);

		final long targetBlockTime = blockTiming.target;

		System.out.println(String.format("mean block period with multiple generators: %d", meanTime));

		assertTrue(Math.abs(meanTime - targetBlockTime) < (targetBlockTime * marginPct / 100));
	}

	@Test
	public void testBlockIntervalCompliance() throws DataException {
		final int numberRounds = BlockChain.getInstance().getBlockTimingByHeight(1_000_000).height - 1;
		final int numberContestants = 25;

		Random random = new Random();
		byte[] randomKey = new byte[32];

		BlockData previousBlockData = repository.getBlockRepository().getLastBlock();
		for (int height = 2; height < numberRounds; ++height) {
			for (int ci = 0; ci < numberContestants; ++ci) {
				random.nextBytes(randomKey);
				long newTimestamp = Block.calcMinimumTimestamp(previousBlockData, randomKey);

				long legacyTimestamp = calcLegacyMinimumTimestamp(previousBlockData, randomKey);

				assertEquals(String.format("Incorrect timestamp at height %d", height), legacyTimestamp, newTimestamp);
			}

			Block newBlock = new Block(repository, previousBlockData, aliceAccount, previousBlockData.getTimestamp() + 1);
			newBlock.sign();
			newBlock.process();
			repository.saveChanges();

			previousBlockData = newBlock.getBlockData();
		}
	}

	private long calcLegacyMinimumTimestamp(BlockData parentBlockData, byte[] generatorPublicKey) {
		BigInteger distance = Block.calcGeneratorDistance(parentBlockData, generatorPublicKey);

		final int minBlockTime = 30; // seconds
		final int maxBlockTime = 90; // seconds

		long timeOffset = distance.multiply(BigInteger.valueOf((maxBlockTime - minBlockTime) * 1000L)).divide(MAX_DISTANCE).longValue();

		// 'distance' is at most half the size of MAX_DISTANCE, so in new code we correct this oversight...
		final int thisHeight = parentBlockData.getHeight() + 1;
		if (thisHeight >= BlockChain.getInstance().getNewBlockDistanceHeight())
			timeOffset *= 2;

		return parentBlockData.getTimestamp() + (minBlockTime * 1000L) + timeOffset;
	}

}
