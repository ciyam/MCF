package org.qora.test;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.qora.block.Block;
import org.qora.block.GenesisBlock;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transaction.Transaction;
import org.qora.transform.TransformationException;
import org.qora.transform.block.BlockTransformer;

import static org.junit.Assert.*;

public class BlockTests extends Common {

	@Test
	public void testGenesisBlockTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			GenesisBlock block = GenesisBlock.getInstance(repository);

			assertNotNull(block);
			assertTrue(block.isSignatureValid());
			// only true if blockchain is empty
			// assertTrue(block.isValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions) {
				assertNotNull(transaction);

				TransactionData transactionData = transaction.getTransactionData();

				assertEquals(Transaction.TransactionType.GENESIS, transactionData.getType());
				assertTrue(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
				assertNull(transactionData.getReference());
				assertTrue(transaction.isSignatureValid());
				assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
			}

			// Attempt to load first transaction directly from database
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(transactions.get(0).getTransactionData().getSignature());
			assertNotNull(transactionData);

			assertEquals(Transaction.TransactionType.GENESIS, transactionData.getType());
			assertTrue(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
			assertNull(transactionData.getReference());

			Transaction transaction = Transaction.fromData(repository, transactionData);
			assertNotNull(transaction);

			assertTrue(transaction.isSignatureValid());
			assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
		}
	}

	@Test
	public void testBlockPaymentTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Block 949 has lots of varied transactions
			// Blocks 390 & 754 have only payment transactions
			BlockData blockData = repository.getBlockRepository().fromHeight(754);
			assertNotNull("Block 754 is required for this test", blockData);

			Block block = new Block(repository, blockData);
			assertTrue(block.isSignatureValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions) {
				assertNotNull(transaction);

				TransactionData transactionData = transaction.getTransactionData();

				assertEquals(Transaction.TransactionType.PAYMENT, transactionData.getType());
				assertFalse(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
				assertNotNull(transactionData.getReference());

				assertTrue(transaction.isSignatureValid());
			}

			// Attempt to load first transaction directly from database
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(transactions.get(0).getTransactionData().getSignature());
			assertNotNull(transactionData);

			assertEquals(Transaction.TransactionType.PAYMENT, transactionData.getType());
			assertFalse(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
			assertNotNull(transactionData.getReference());

			Transaction transaction = Transaction.fromData(repository, transactionData);
			assertNotNull(transaction);

			assertTrue(transaction.isSignatureValid());
		}
	}

	@Test
	public void testBlockSerialization() throws DataException, TransformationException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Block 949 has lots of varied transactions
			// Blocks 390 & 754 have only payment transactions
			BlockData blockData = repository.getBlockRepository().fromHeight(754);
			assertNotNull("Block 754 is required for this test", blockData);

			Block block = new Block(repository, blockData);
			assertTrue(block.isSignatureValid());

			byte[] bytes = BlockTransformer.toBytes(block);

			assertEquals(BlockTransformer.getDataLength(block), bytes.length);
		}
	}

}
