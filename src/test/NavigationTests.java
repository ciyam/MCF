package test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository transactionRepository = repository.getTransactionRepository();

			assertTrue(repository.getBlockRepository().getBlockchainHeight() >= 49778,
				"Migrate from old database to at least block 49778 before running this test");

			String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
			byte[] signature = Base58.decode(signature58);

			System.out.println("Navigating to Block from transaction " + signature58);

			TransactionData transactionData = transactionRepository.fromSignature(signature);
			assertNotNull(transactionData, "Transaction data not loaded from repository");
			assertEquals(TransactionType.PAYMENT, transactionData.getType(), "Transaction data not PAYMENT type");

			BlockData blockData = transactionRepository.getBlockDataFromSignature(signature);
			assertNotNull(blockData, "Block 49778 not loaded from database");

			System.out.println("Block " + blockData.getHeight() + ", signature: " + Base58.encode(blockData.getSignature()));

			assertEquals((Integer) 49778, blockData.getHeight());
		}
	}

}
