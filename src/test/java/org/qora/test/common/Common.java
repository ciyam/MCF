package org.qora.test.common;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.net.URL;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Base58;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.data.account.AccountBalanceData;
import org.qora.data.asset.AssetData;
import org.qora.data.block.BlockData;
import org.qora.data.group.GroupData;
import org.qora.repository.AccountRepository.BalanceOrdering;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;

public class Common {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
	}

	private static final Logger LOGGER = LogManager.getLogger(Common.class);

	public static final String testConnectionUrl = "jdbc:hsqldb:mem:testdb";
	// For debugging, use this instead to write DB to disk for examination:
	// public static final String testConnectionUrl = "jdbc:hsqldb:file:testdb/blockchain;create=true";

	public static final String testSettingsFilename = "test-settings-v2.json";

	static {
		// Load/check settings, which potentially sets up blockchain config, etc.
		URL testSettingsUrl = Common.class.getClassLoader().getResource(testSettingsFilename);
		assertNotNull("Test settings JSON file not found", testSettingsUrl);
		Settings.fileInstance(testSettingsUrl.getPath());
	}

	private static List<AssetData> initialAssets;
	private static List<GroupData> initialGroups;
	private static List<AccountBalanceData> initialBalances;

	// TODO: converts users of these constants to TestAccount schema
	public static final byte[] v2testPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
	public static final byte[] v2testPublicKey = Base58.decode("2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP");
	public static final String v2testAddress = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v";

	private static Map<String, TestAccount> testAccountsByName = new HashMap<>();
	static {
		testAccountsByName.put("alice", new TestAccount(null, "alice", "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6"));
		testAccountsByName.put("bob", new TestAccount(null, "bob", "AdTd9SUEYSdTW8mgK3Gu72K97bCHGdUwi2VvLNjUohot"));
		testAccountsByName.put("chloe", new TestAccount(null, "chloe", "HqVngdE1AmEyDpfwTZqUdFHB13o4bCmpoTNAKEqki66K"));
		testAccountsByName.put("dilbert", new TestAccount(null, "dilbert", "Gakhh6Ln4vtBFM88nE9JmDaLBDtUBg51aVFpWfSkyVw5"));
	}

	public static TestAccount getTestAccount(Repository repository, String name) {
		return new TestAccount(repository, name, testAccountsByName.get(name).getSeed());
	}

	public static List<TestAccount> getTestAccounts(Repository repository) {
		return testAccountsByName.values().stream().map(account -> new TestAccount(repository, account.accountName, account.getSeed())).collect(Collectors.toList());
	}

	public static void useSettings(String settingsFilename) throws DataException {
		closeRepository();

		// Load/check settings, which potentially sets up blockchain config, etc.
		LOGGER.debug(String.format("Using setting file: %s", settingsFilename));
		URL testSettingsUrl = Common.class.getClassLoader().getResource(settingsFilename);
		assertNotNull("Test settings JSON file not found", testSettingsUrl);
		Settings.fileInstance(testSettingsUrl.getPath());

		setRepository();

		resetBlockchain();
	}

	public static void useDefaultSettings() throws DataException {
		useSettings(testSettingsFilename);
	}

	public static void resetBlockchain() throws DataException {
		BlockChain.validate();

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Build snapshot of initial state in case we want to compare with post-test orphaning
			initialAssets = repository.getAssetRepository().getAllAssets();
			initialGroups = repository.getGroupRepository().getAllGroups();
			initialBalances = repository.getAccountRepository().getAssetBalances(Collections.emptyList(), Collections.emptyList(), BalanceOrdering.ASSET_ACCOUNT, null, null, null);

			// Check that each test account can fetch their last reference
			for (TestAccount testAccount : getTestAccounts(repository))
				assertNotNull(String.format("Test account '%s' should have existing transaction", testAccount.accountName), testAccount.getLastReference());
		}
	}

	/** Orphan back to genesis block and compare initial snapshot. */
	public static void orphanCheck() throws DataException {
		LOGGER.debug("Orphaning back to genesis block");

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Orphan back to genesis block
			while (repository.getBlockRepository().getBlockchainHeight() > 1) {
				BlockData blockData = repository.getBlockRepository().getLastBlock();
				Block block = new Block(repository, blockData);
				block.orphan();
				repository.saveChanges();
			}

			List<AssetData> remainingAssets = repository.getAssetRepository().getAllAssets();
			checkOrphanedLists("asset", initialAssets, remainingAssets, AssetData::getAssetId);

			List<GroupData> remainingGroups = repository.getGroupRepository().getAllGroups();
			checkOrphanedLists("group", initialGroups, remainingGroups, GroupData::getGroupId);

			List<AccountBalanceData> remainingBalances = repository.getAccountRepository().getAssetBalances(Collections.emptyList(), Collections.emptyList(), BalanceOrdering.ASSET_ACCOUNT, null, null, null);
			checkOrphanedLists("account balance", initialBalances, remainingBalances, entry -> entry.getAssetName() + "-" + entry.getAddress());

			assertEquals("remainingBalances is different size", initialBalances.size(), remainingBalances.size());
			// Actually compare balances
			for (int i = 0; i < initialBalances.size(); ++i) {
				AccountBalanceData initialBalance = initialBalances.get(i);
				AccountBalanceData remainingBalance = remainingBalances.get(i);

				assertEquals("Remaining balance's asset differs", initialBalance.getAssetId(), remainingBalance.getAssetId());
				assertEquals("Remaining balance's address differs", initialBalance.getAddress(), remainingBalance.getAddress());

				assertEqualBigDecimals("Remaining balance differs", initialBalance.getBalance(), remainingBalance.getBalance());
			}
		}
	}

	private static <T> void checkOrphanedLists(String typeName, List<T> initial, List<T> remaining, Function<T, ? extends Object> keyExtractor) {
		Predicate<T> isInitial = entry -> initial.stream().anyMatch(initialEntry -> keyExtractor.apply(initialEntry).equals(keyExtractor.apply(entry)));
		Predicate<T> isRemaining = entry -> remaining.stream().anyMatch(remainingEntry -> keyExtractor.apply(remainingEntry).equals(keyExtractor.apply(entry)));

		// Check all initial entries remain
		for (T initialEntry : initial)
			assertTrue(String.format("Genesis %s %s missing", typeName, keyExtractor.apply(initialEntry)), isRemaining.test(initialEntry));

		// Remove initial entries from remaining to see there are any leftover
		List<T> remainingClone = new ArrayList<T>(remaining);
		remainingClone.removeIf(isInitial);

		assertTrue(String.format("Non-genesis %s remains", typeName), remainingClone.isEmpty());
	}

	@BeforeClass
	public static void setRepository() throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(testConnectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);
	}

	@AfterClass
	public static void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

	public static void assertEmptyBlockchain(Repository repository) throws DataException {
		assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
	}

	public static void assertEqualBigDecimals(String message, BigDecimal expected, BigDecimal actual) {
		assertTrue(String.format("%s: expected %s, actual %s", message, expected.toPlainString(), actual.toPlainString()),
				actual.compareTo(expected) == 0);
	}

}
