package org.qora.test;

import org.junit.Test;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.repository.TransactionRepository;
import org.qora.test.common.Common;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.utils.Base58;

import static org.junit.Assert.*;

public class NavigationTests extends Common {

	@Test
	public void testNavigateFromTransactionToBlock() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository transactionRepository = repository.getTransactionRepository();

			assertTrue("Migrate from old database to at least block 49778 before running this test", repository.getBlockRepository().getBlockchainHeight() >= 49778);

			String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
			byte[] signature = Base58.decode(signature58);

			System.out.println("Navigating to Block from transaction " + signature58);

			TransactionData transactionData = transactionRepository.fromSignature(signature);
			assertNotNull("Transaction data not loaded from repository", transactionData);
			assertEquals("Transaction data not PAYMENT type", TransactionType.PAYMENT, transactionData.getType());

			int transactionHeight = transactionRepository.getHeightFromSignature(signature);
			assertFalse("Transaction not found or transaction's block not found", transactionHeight == 0);
			assertEquals("Transaction's block height expected to be 49778", 49778, transactionHeight);

			BlockData blockData = repository.getBlockRepository().fromHeight(transactionHeight);
			assertNotNull("Block 49778 not loaded from database", blockData);
			System.out.println("Block " + blockData.getHeight() + ", signature: " + Base58.encode(blockData.getSignature()));

			assertEquals((Integer) 49778, blockData.getHeight());
		}
	}

}
