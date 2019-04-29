package org.qora.test;

import org.junit.Test;
import org.qora.account.Account;
import org.qora.asset.Asset;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RepositoryTests extends Common {

	private static final Logger LOGGER = LogManager.getLogger(RepositoryTests.class);

	@Test
	public void testGetRepository() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertNotNull(repository);
		}
	}

	@Test
	public void testMultipleInstances() throws DataException {
		int n_instances = 5;
		Repository[] repositories = new Repository[n_instances];

		for (int i = 0; i < n_instances; ++i) {
			repositories[i] = RepositoryManager.getRepository();
			assertNotNull(repositories[i]);
		}

		for (int i = 0; i < n_instances; ++i) {
			repositories[i].close();
			repositories[i] = null;
		}
	}

	@Test
	public void testAccessAfterClose() throws DataException {
		try (Repository repository = RepositoryManager.getRepository()) {
			assertNotNull(repository);

			repository.close();

			try {
				repository.discardChanges();
				fail();
			} catch (NullPointerException | DataException e) {
			}

			LOGGER.warn("Expect \"repository already closed\" complaint below");
		}
	}

	@Test
	public void testDeadlock() throws DataException {
		Common.useDefaultSettings();

		// Open connection 1
		try (Repository repository1 = RepositoryManager.getRepository()) {

			// Do a database 'read'
			Account account1 = Common.getTestAccount(repository1, "alice");
			account1.getLastReference();

			// Open connection 2
			try (Repository repository2 = RepositoryManager.getRepository()) {
				// Update account in 2
				Account account2 = Common.getTestAccount(repository2, "alice");
				account2.setConfirmedBalance(Asset.QORA, BigDecimal.valueOf(1234L));
				repository2.saveChanges();
			}

			repository1.discardChanges();

			// Update account in 1
			account1.setConfirmedBalance(Asset.QORA, BigDecimal.valueOf(5678L));
			repository1.saveChanges();
		}
	}

}
