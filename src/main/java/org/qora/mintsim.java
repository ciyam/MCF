package org.qora;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class mintsim {

	private static final int NUMBER_BLOCKS = 5_000_000;
	private static final double GRANT_PROB = 0.001;
	private static final int BLOCK_HISTORY = 0;
	private static final int WEIGHTING = 2;

	private static final int TOP_MINTERS_SIZE = 200;

	private static final Random random = new Random();
	private static List<TierInfo> tiers = new ArrayList<>();
	private static List<Account> accounts = new ArrayList<>();
	private static List<Integer> blockMinters = new ArrayList<>();

	private static List<Integer> accountsWithGrants = new ArrayList<>();

	public static class TierInfo {
		public final int maxAccounts;
		public final int minBlocks;
		public int numberAccounts;

		public TierInfo(int maxAccounts, int minBlocks) {
			this.maxAccounts = maxAccounts;
			this.minBlocks = minBlocks;
			this.numberAccounts = 0;
		}
	}

	public static class Account {
		public final int tierIndex;
		public int blocksForged;
		public int rightsGranted;

		public Account(int tierIndex) {
			this.tierIndex = tierIndex;
			this.blocksForged = 0;
			this.rightsGranted = 0;
		}
	}

	public static void main(String args[]) {
		if (args.length < 2 || (args.length % 2) != 0) {
			System.err.println("usage: mintsim <tier1-min-blocks> <number-tier1-accounts> [<tier2-min-blocks> <max-tier2-per-tier1> [...]]");
			System.exit(1);
		}

		try {
			int argIndex = 0;
			do {
				int minBlocks = Integer.valueOf(args[argIndex++]);
				int maxAccounts = Integer.valueOf(args[argIndex++]);

				tiers.add(new TierInfo(maxAccounts, minBlocks));
			} while (argIndex < args.length);
		} catch (NumberFormatException e) {
			System.err.println("Can't parse number?");
			System.exit(2);
		}

		// Print summary
		System.out.println(String.format("Number of tiers: %d", tiers.size()));

		for (int i = 0; i < tiers.size(); ++i) {
			TierInfo tier = tiers.get(i);
			System.out.println(String.format("Tier %d:", i));
			System.out.println(String.format("\tMinimum forged blocks to grant right: %d", tier.minBlocks));
			System.out.println(String.format("\tMaximum tier%d grants: %d", i + 1, tier.maxAccounts));
		}

		TierInfo initialTier = tiers.get(0);

		int totalAccounts = initialTier.maxAccounts;
		for (int i = 1; i < tiers.size(); ++i)
			totalAccounts *= 1 + tiers.get(i).maxAccounts;

		System.out.println(String.format("Total accounts: %d", totalAccounts));

		// Create initial accounts
		initialTier.numberAccounts = initialTier.maxAccounts;
		for (int i = 0; i < initialTier.maxAccounts; ++i)
			accounts.add(new Account(0));

		for (int height = 1; height < NUMBER_BLOCKS; ++height) {
			int minterId = pickMinterId();
			Account minter = accounts.get(minterId);

			++minter.blocksForged;
			blockMinters.add(minterId);

			if (minter.tierIndex < tiers.size() - 1) {
				TierInfo nextTier = tiers.get(minter.tierIndex + 1);

				// Minter just reached threshold to grant rights
				if (minter.blocksForged == nextTier.minBlocks)
					accountsWithGrants.add(minterId);
			}

			List<Integer> accountsToRemove = new ArrayList<>();
			// Do any account with spare grants want to grant?
			for (int granterId : accountsWithGrants) {
				if (random.nextDouble() >= GRANT_PROB)
					continue;

				Account granter = accounts.get(granterId);
				TierInfo nextTier = tiers.get(granter.tierIndex + 1);

				accounts.add(new Account(granter.tierIndex + 1));

				++nextTier.numberAccounts;
				++granter.rightsGranted;

				if (granter.rightsGranted == nextTier.maxAccounts)
					accountsToRemove.add(granterId);
			}

			// Remove granters that have used their allowance
			accountsWithGrants.removeAll(accountsToRemove);

			if (height % 100000 == 0) {
				System.out.println(String.format("Summary after block %d:", height));
				for (int i = 0; i < tiers.size(); ++i)
					System.out.println(String.format("\tTier %d: number of accounts: %d", i, tiers.get(i).numberAccounts));
			}
		}

		// Top minters
		List<Integer> topMinters = new ArrayList<>();
		for (int i = 0; i < accounts.size(); ++i) {
			topMinters.add(i);
			topMinters.sort((a, b) -> Integer.compare(accounts.get(b).blocksForged, accounts.get(a).blocksForged));

			if (topMinters.size() > TOP_MINTERS_SIZE)
				topMinters.remove(TOP_MINTERS_SIZE);
		}

		System.out.println(String.format("Top %d minters:", TOP_MINTERS_SIZE));
		for (int i = 0; i < topMinters.size(); ++i) {
			int topMinterId = topMinters.get(i);
			Account topMinter = accounts.get(topMinterId);
			System.out.println(String.format("\tAccount %d (tier %d) has minted %d blocks", topMinterId, topMinter.tierIndex, topMinter.blocksForged));
		}

		for (int i = 0; i < tiers.size(); ++i)
			System.out.println(String.format("Tier %d: number of accounts: %d", i, tiers.get(i).numberAccounts));
	}

	private static int pickMinterId() {
		// There might not be enough block history yet...
		final int blockHistory = Math.min(BLOCK_HISTORY, blockMinters.size());

		// Weighting (W)

		// An account that HASN'T forged in the last X blocks has 1 standard chance to forge
		// but an account that HAS forged Y in the last X blocks has 1 + (Y / X) * W chances to forge
		// e.g. forged 25 in last 100 blocks, with weighting 8, gives (25 / 100) * 8 = 2 extra chances

		// So in X blocks there will be X * W extra chances.
		// We pick winning number from (number-of-accounts + X * W) chances
		int totalChances = accounts.size() + blockHistory * WEIGHTING;
		int winningNumber = random.nextInt(totalChances);

		// Simple case if winning number is less than number of accounts,
		// otherwise we need to handle extra chances for accounts that have forged in last X blocks.
		if (winningNumber < accounts.size())
			return winningNumber;

		// Handling extra chances

		// We can work out which block in last X blocks as each block is worth W chances
		int blockOffset = (winningNumber - accounts.size()) / WEIGHTING;
		int blockIndex = blockMinters.size() - 1 - blockOffset;

		return blockMinters.get(blockIndex);
	}

}
