package org.qora.test.forging;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.data.account.ProxyForgerData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.BlockUtils;
import org.qora.test.common.Common;
import org.qora.utils.Base58;

public class ProxyForgingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testCreateRelationship() throws DataException {
		final BigDecimal share = new BigDecimal("12.8");

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");

			// Create relationship
			byte[] proxyPrivateKey = AccountUtils.proxyForging(repository, "alice", "bob", share);
			PrivateKeyAccount proxyAccount = new PrivateKeyAccount(repository, proxyPrivateKey);

			// Confirm relationship info set correctly

			// Fetch using proxy public key
			ProxyForgerData proxyForgerData = repository.getAccountRepository().getProxyForgeData(proxyAccount.getPublicKey());
			assertEquals("Incorrect generator public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(proxyForgerData.getForgerPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), proxyForgerData.getRecipient());
			assertEqualBigDecimals("Incorrect reward share", share, proxyForgerData.getShare());

			// Fetch using generator public key and recipient address combination
			proxyForgerData = repository.getAccountRepository().getProxyForgeData(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertEquals("Incorrect generator public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(proxyForgerData.getForgerPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), proxyForgerData.getRecipient());
			assertEqualBigDecimals("Incorrect reward share", share, proxyForgerData.getShare());

			// Delete relationship
			byte[] newProxyPrivateKey = AccountUtils.proxyForging(repository, "alice", "bob", BigDecimal.ZERO);
			PrivateKeyAccount newProxyAccount = new PrivateKeyAccount(repository, newProxyPrivateKey);

			// Confirm proxy keys match
			assertEquals("Proxy private keys differ", Base58.encode(proxyPrivateKey), Base58.encode(newProxyPrivateKey));
			assertEquals("Proxy public keys differ", Base58.encode(proxyAccount.getPublicKey()), Base58.encode(newProxyAccount.getPublicKey()));

			// Confirm relationship no longer exists in repository

			// Fetch using proxy public key
			ProxyForgerData newProxyForgerData = repository.getAccountRepository().getProxyForgeData(proxyAccount.getPublicKey());
			assertNull("Proxy relationship data shouldn't exist", newProxyForgerData);

			// Fetch using generator public key and recipient address combination
			newProxyForgerData = repository.getAccountRepository().getProxyForgeData(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNull("Proxy relationship data shouldn't exist", newProxyForgerData);

			// Orphan last block to restore prior proxy relationship
			BlockUtils.orphanLastBlock(repository);

			// Confirm proxy relationship restored correctly

			// Fetch using proxy public key
			newProxyForgerData = repository.getAccountRepository().getProxyForgeData(proxyAccount.getPublicKey());
			assertNotNull("Proxy relationship data should have been restored", newProxyForgerData);
			assertEquals("Incorrect generator public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(newProxyForgerData.getForgerPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), newProxyForgerData.getRecipient());
			assertEqualBigDecimals("Incorrect reward share", share, newProxyForgerData.getShare());

			// Fetch using generator public key and recipient address combination
			newProxyForgerData = repository.getAccountRepository().getProxyForgeData(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNotNull("Proxy relationship data should have been restored", newProxyForgerData);
			assertEquals("Incorrect generator public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(newProxyForgerData.getForgerPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), newProxyForgerData.getRecipient());
			assertEqualBigDecimals("Incorrect reward share", share, newProxyForgerData.getShare());

			// Orphan another block to remove initial proxy relationship
			BlockUtils.orphanLastBlock(repository);

			// Confirm proxy relationship no longer exists

			// Fetch using proxy public key
			newProxyForgerData = repository.getAccountRepository().getProxyForgeData(proxyAccount.getPublicKey());
			assertNull("Proxy relationship data shouldn't exist", newProxyForgerData);

			// Fetch using generator public key and recipient address combination
			newProxyForgerData = repository.getAccountRepository().getProxyForgeData(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNull("Proxy relationship data shouldn't exist", newProxyForgerData);
		}
	}

}
