package org.qora.test;

import static org.junit.Assert.assertEquals;

import java.security.Security;

import org.bitcoinj.core.Base58;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;

public class Common {

	public static final String testConnectionUrl = "jdbc:hsqldb:mem:testdb";
	public static final String testSettingsFilename = "test-settings.json";

	public static final byte[] v2testPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
	public static final byte[] v2testPublicKey = Base58.decode("2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP");
	public static final String v2testAddress = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v";

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.fileInstance(testSettingsFilename);
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

	public static void assetEmptyBlockchain(Repository repository) throws DataException {
		assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
	}

}
