package test;

import static org.junit.Assert.*;

import org.junit.Test;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.transaction.Transaction.TransactionType;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import repository.TransactionRepository;
import utils.Base58;

public class NavigationTests extends Common {

	@Test
	public void testNavigateFromTransactionToBlock() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		TransactionRepository transactionRepository = repository.getTransactionRepository();

		assertTrue("Migrate from old database to at least block 49778 before running this test",
				repository.getBlockRepository().getBlockchainHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		System.out.println("Navigating to Block from transaction " + signature58);

		TransactionData transactionData = transactionRepository.fromSignature(signature);
		assertNotNull("Transaction data not loaded from repository", transactionData);
		assertEquals("Transaction data not PAYMENT type", TransactionType.PAYMENT, transactionData.getType().value);

		BlockData blockData = transactionRepository.toBlock(transactionData);
		assertNotNull("Block 49778 not loaded from database", blockData);

		System.out.println("Block " + blockData.getHeight() + ", signature: " + Base58.encode(blockData.getSignature()));

		assertEquals(49778, blockData.getHeight());
	}

}
