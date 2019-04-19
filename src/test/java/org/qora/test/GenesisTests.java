package org.qora.test;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.qora.block.Block;
import org.qora.block.GenesisBlock;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transaction.Transaction;

import static org.junit.Assert.*;

public class GenesisTests extends Common {

	@Test
	public void testGenesisBlockTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());

			GenesisBlock block = GenesisBlock.getInstance(repository);

			assertNotNull("No genesis block?", block);
			assertTrue(block.isSignatureValid());
			// Note: only true if blockchain is empty
			assertEquals("Block invalid", Block.ValidationResult.OK, block.isValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull("No transactions?", transactions);

			for (Transaction transaction : transactions) {
				assertNotNull(transaction);

				TransactionData transactionData = transaction.getTransactionData();

				assertEquals(Transaction.TransactionType.GENESIS, transactionData.getType());
				assertTrue(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
				assertNull(transactionData.getReference());
				assertNotNull(transactionData.getSignature());
				assertTrue(transaction.isSignatureValid());
				assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
			}

			// Actually try to process genesis block onto empty blockchain
			block.process();
			repository.saveChanges();
		}
	}

}
