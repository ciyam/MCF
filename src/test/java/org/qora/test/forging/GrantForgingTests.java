package org.qora.test.forging;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.AccountUtils;
import org.qora.test.common.Common;
import org.qora.test.common.TransactionUtils;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;

public class GrantForgingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimpleGrant() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, "alice");

			TransactionData transactionData = AccountUtils.createEnableForging(repository, "alice", "bob");
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(forgingAccount);

			ValidationResult result = transaction.isValidUnconfirmed();
			// Alice can't grant without forging minimum number of blocks
			assertEquals(ValidationResult.FORGE_MORE_BLOCKS, result);

			// Forge a load of blocks
			int blocksNeeded = BlockChain.getInstance().getForgingTiers().get(0).minBlocks;
			for (int i = 0; i < blocksNeeded; ++i)
				BlockGenerator.generateTestingBlock(repository, forgingAccount);

			// Alice should be able to grant now
			result = transaction.isValidUnconfirmed();
			assertEquals(ValidationResult.OK, result);

			TransactionUtils.signAndForge(repository, transactionData, forgingAccount);
		}
	}

	@Test
	public void testMaxGrant() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount forgingAccount = Common.getTestAccount(repository, "alice");

			TransactionData transactionData = AccountUtils.createEnableForging(repository, "alice", "bob");
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(forgingAccount);

			ValidationResult result = transaction.isValidUnconfirmed();
			// Alice can't grant without forging minimum number of blocks
			assertEquals(ValidationResult.FORGE_MORE_BLOCKS, result);

			// Forge a load of blocks
			int blocksNeeded = BlockChain.getInstance().getForgingTiers().get(0).minBlocks;
			for (int i = 0; i < blocksNeeded; ++i)
				BlockGenerator.generateTestingBlock(repository, forgingAccount);

			// Alice should be able to grant up to 5 now

			// Gift to random accounts
			Random random = new Random();
			for (int i = 0; i < 5; ++i) {
				byte[] publicKey = new byte[32];
				random.nextBytes(publicKey);

				transactionData = AccountUtils.createEnableForging(repository, "alice", publicKey);
				transaction = Transaction.fromData(repository, transactionData);
				transaction.sign(forgingAccount);

				result = transaction.isValidUnconfirmed();
				assertEquals("Couldn't enable account #" + i, ValidationResult.OK, result);

				TransactionUtils.signAndForge(repository, transactionData, forgingAccount);
			}

			// Alice's allocation used up
			byte[] publicKey = new byte[32];
			random.nextBytes(publicKey);

			transactionData = AccountUtils.createEnableForging(repository, "alice", publicKey);
			transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(forgingAccount);

			result = transaction.isValidUnconfirmed();
			assertEquals(ValidationResult.FORGING_ENABLE_LIMIT, result);
		}
	}

	@Test
	public void testFinalTier() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");

			TransactionData transactionData = AccountUtils.createEnableForging(repository, "alice", "bob");
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(aliceAccount);

			ValidationResult result = transaction.isValidUnconfirmed();
			// Alice can't grant without forging minimum number of blocks
			assertEquals(ValidationResult.FORGE_MORE_BLOCKS, result);

			// Forge a load of blocks
			int blocksNeeded = BlockChain.getInstance().getForgingTiers().get(0).minBlocks;
			for (int i = 0; i < blocksNeeded; ++i)
				BlockGenerator.generateTestingBlock(repository, aliceAccount);

			// Alice should be able to grant now
			AccountUtils.enableForging(repository, "alice", "bob");

			// Bob can't grant without forging minimum number of blocks
			transactionData = AccountUtils.createEnableForging(repository, "bob", "chloe");
			transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(aliceAccount);

			result = transaction.isValidUnconfirmed();
			assertEquals(ValidationResult.FORGE_MORE_BLOCKS, result);

			// Bob needs to forge a load of blocks
			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			blocksNeeded = BlockChain.getInstance().getForgingTiers().get(1).minBlocks;
			for (int i = 0; i < blocksNeeded; ++i)
				BlockGenerator.generateTestingBlock(repository, bobAccount);

			// Bob should be able to grant now
			AccountUtils.enableForging(repository, "bob", "chloe");

			// Chloe is final tier so shouldn't be able to grant
			Random random = new Random();
			byte[] publicKey = new byte[32];
			random.nextBytes(publicKey);

			transactionData = AccountUtils.createEnableForging(repository, "chloe", publicKey);
			transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(aliceAccount);

			result = transaction.isValidUnconfirmed();
			assertEquals(ValidationResult.FORGING_ENABLE_LIMIT, result);
		}
	}

}
