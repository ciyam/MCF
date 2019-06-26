package org.qora.test.block;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.data.block.BlockSummaryData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.Common;

import com.google.common.primitives.Bytes;

public class BlockchainTests extends Common {

	private static final NumberFormat formatter = new DecimalFormat("0.###E0");

	private byte[] previousSignature;

	private Repository repository;

	private PrivateKeyAccount aliceAccount;
	private PrivateKeyAccount bobAccount;
	private PrivateKeyAccount chloeAccount;

	private PrivateKeyAccount aliceDilbert;
	private PrivateKeyAccount bobDilbert;
	private PrivateKeyAccount chloeDilbert;

	private List<PrivateKeyAccount> allAccounts;

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-v2-subsets.json");

		BigDecimal share = new BigDecimal("0.5");

		repository = RepositoryManager.getRepository();

		aliceAccount = Common.getTestAccount(repository, "alice");
		bobAccount = Common.getTestAccount(repository, "bob");
		chloeAccount = Common.getTestAccount(repository, "chloe");

		byte[] aliceDilbertKey = AccountUtils.proxyForging(repository, "alice", "dilbert", share);
		byte[] bobDilbertKey = AccountUtils.proxyForging(repository, "bob", "dilbert", share);
		byte[] chloeDilbertKey = AccountUtils.proxyForging(repository, "chloe", "dilbert", share);

		aliceDilbert = new PrivateKeyAccount(repository, aliceDilbertKey);
		bobDilbert = new PrivateKeyAccount(repository, bobDilbertKey);
		chloeDilbert = new PrivateKeyAccount(repository, chloeDilbertKey);

		allAccounts = Arrays.asList(aliceAccount, bobAccount, chloeAccount, aliceDilbert, bobDilbert, chloeDilbert);

		previousSignature = Crypto.digest(new byte[32]);
	}

	@After
	public void afterTest() throws DataException {
		repository.close();
		repository = null;
	}

	private BlockSummaryData generateSummary(BlockSummaryData previousSummary) throws DataException {
		Random random = new Random();
		PrivateKeyAccount generator = allAccounts.get(random.nextInt(allAccounts.size()));
		byte[] signature = Crypto.digest(Bytes.concat(generator.getPrivateKey(), new byte[32], previousSummary.getSignature()));
		return new BlockSummaryData(previousSummary.getHeight() + 1, signature, generator.getPublicKey());
	}

	private BlockSummaryData generateSummary(int height, PrivateKeyAccount generator) {
		previousSignature = Crypto.digest(Bytes.concat(previousSignature, new byte[32], generator.getPrivateKey()));
		return new BlockSummaryData(height, previousSignature, generator.getPublicKey());
	}

	private List<BlockSummaryData> generateOldAlgorithmSummaries() {
		List<BlockSummaryData> blockSummaries = new ArrayList<>();
		blockSummaries.add(generateSummary(5, aliceAccount));
		blockSummaries.add(generateSummary(6, bobAccount));
		blockSummaries.add(generateSummary(7, aliceDilbert));
		blockSummaries.add(generateSummary(8, bobDilbert));
		blockSummaries.add(generateSummary(9, chloeDilbert));
		return blockSummaries;
	}

	private List<BlockSummaryData> generateNewAlgorithmSummaries() {
		List<BlockSummaryData> blockSummaries = new ArrayList<>();
		blockSummaries.add(generateSummary(10, aliceAccount));
		blockSummaries.add(generateSummary(11, bobAccount));
		blockSummaries.add(generateSummary(12, aliceDilbert));
		blockSummaries.add(generateSummary(13, bobDilbert));
		blockSummaries.add(generateSummary(14, chloeDilbert));
		return blockSummaries;
	}

	@Test
	public void testOldAlgorithm() throws DataException {
		String expectedDistance = "5.743E76";

		List<BlockSummaryData> blockSummaries = generateOldAlgorithmSummaries();
		BlockSummaryData parentBlockSummary = blockSummaries.remove(0);

		List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, Arrays.asList(blockSummaries));
		BigInteger distance = distances.get(0);

		assertEquals(expectedDistance, formatter.format(distance));
	}

	@Test
	public void testNewAlgorithm() throws DataException {
		String expectedDistance = "2.994E76";

		List<BlockSummaryData> blockSummaries = generateNewAlgorithmSummaries();
		BlockSummaryData parentBlockSummary = blockSummaries.remove(0);

		List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, Arrays.asList(blockSummaries));
		BigInteger distance = distances.get(0);

		assertEquals(expectedDistance, formatter.format(distance));
	}

	@Test
	public void testMixed() throws DataException {
		String expectedDistance = "8.906E76";

		List<BlockSummaryData> blockSummaries = generateOldAlgorithmSummaries();
		blockSummaries.addAll(generateNewAlgorithmSummaries());
		BlockSummaryData parentBlockSummary = blockSummaries.remove(0);

		List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, Arrays.asList(blockSummaries));
		BigInteger distance = distances.get(0);

		assertEquals(expectedDistance, formatter.format(distance));
	}

	@Test
	public void testPaddedShorterSubset() throws DataException {
		List<BlockSummaryData> shorterSubset = generateNewAlgorithmSummaries();
		BlockSummaryData parentBlockSummary = shorterSubset.remove(0);

		List<BlockSummaryData> longerSubset = new ArrayList<>(shorterSubset);
		BlockSummaryData previousSummary = longerSubset.get(longerSubset.size() - 1);
		for (int i = 0; i < 5; ++i) {
			previousSummary = generateSummary(previousSummary);
			longerSubset.add(previousSummary);
		}

		assertTrue(shorterSubset.size() < longerSubset.size());

		List<List<BlockSummaryData>> subsets = Arrays.asList(shorterSubset, longerSubset, shorterSubset, longerSubset);

		List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, subsets);

		assertEquals(distances.get(0), distances.get(2));
		assertEquals(distances.get(1), distances.get(3));
		assertFalse(distances.get(0).equals(distances.get(1)));
	}

	@Test
	public void testWinningDistribution() throws DataException {
		BigDecimal share = new BigDecimal("0.5");

		// Two minting accounts, each with two proxy-forging relationships
		byte[] aliceChloeKey = AccountUtils.proxyForging(repository, "alice", "chloe", share);
		byte[] bobChloeKey = AccountUtils.proxyForging(repository, "bob", "chloe", share);
		byte[] bobDilbertKey = AccountUtils.proxyForging(repository, "bob", "dilbert", share);

		PrivateKeyAccount aliceChloe = new PrivateKeyAccount(repository, aliceChloeKey);
		PrivateKeyAccount bobChloe = new PrivateKeyAccount(repository, bobChloeKey);
		PrivateKeyAccount bobDilbert = new PrivateKeyAccount(repository, bobDilbertKey);

		List<PrivateKeyAccount> allAccounts = Arrays.asList(aliceChloe, bobChloe, bobDilbert);

		int[] wins = new int[allAccounts.size()];
		BlockSummaryData parentBlockSummary = generateSummary(10, aliceAccount);

		// Run through a ton of blocks
		for (int height = 11; height < 10000; ++height) {
			List<List<BlockSummaryData>> subsets = new ArrayList<>();
			for (PrivateKeyAccount account : allAccounts)
				subsets.add(Arrays.asList(generateSummary(height, account)));

			List<BigInteger> distances = BlockChain.calcBlockchainDistances(repository, parentBlockSummary, subsets);

			int smallestDistanceIndex = 0;
			for (int index = 1; index < distances.size(); ++index)
				if (distances.get(index).compareTo(distances.get(smallestDistanceIndex)) < 0)
					smallestDistanceIndex = index;

			wins[smallestDistanceIndex]++;

			parentBlockSummary = subsets.get(smallestDistanceIndex).get(0);
		}

		for (int i = 0; i< wins.length; ++i)
			System.out.println(String.format("account %d won %d times", i, wins[i]));

		// We'd expect Alice-related wins to be about twice that of each Bob-related wins
		// and Bob-related wins to be relatively close to each other

		final int marginPct = 5; // tolerance margin (percent)

		// Bob-related wins relatively close to each other:
		assertTrue(Math.abs(wins[1] - wins[2]) < (wins[1] * marginPct / 100));

		// Alice-related wins roughly twice Bob-related wins
		assertTrue(Math.abs(wins[0] - wins[1] * 2) < (wins[0] * marginPct / 100));
	}

}
