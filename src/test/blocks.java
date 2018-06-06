package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import org.junit.Test;

import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;

public class blocks extends common {

	@Test
	public void testGenesisBlockTransactions() throws SQLException {
		GenesisBlock block = GenesisBlock.getInstance();
		assertNotNull(block);
		assertTrue(block.isSignatureValid());
		// only true if blockchain is empty
		// assertTrue(block.isValid(connection));

		List<Transaction> transactions = block.getTransactions();
		assertNotNull(transactions);

		for (Transaction transaction : transactions) {
			assertNotNull(transaction);
			assertEquals(Transaction.TransactionType.GENESIS, transaction.getType());
			assertTrue(transaction.getFee().compareTo(BigDecimal.ZERO) == 0);
			assertNull(transaction.getReference());
			assertTrue(transaction.isSignatureValid());
			assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
		}

		// Attempt to load first transaction directly from database
		Transaction transaction = TransactionFactory.fromSignature(transactions.get(0).getSignature());
		assertNotNull(transaction);
		assertEquals(Transaction.TransactionType.GENESIS, transaction.getType());
		assertTrue(transaction.getFee().compareTo(BigDecimal.ZERO) == 0);
		assertNull(transaction.getReference());
		assertTrue(transaction.isSignatureValid());
		assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
	}

	@Test
	public void testBlockPaymentTransactions() throws SQLException {
		// Block 949 has lots of varied transactions
		// Blocks 390 & 754 have only payment transactions
		Block block = Block.fromHeight(754);
		assertNotNull("Block 754 is required for this test", block);
		assertTrue(block.isSignatureValid());

		List<Transaction> transactions = block.getTransactions();
		assertNotNull(transactions);

		for (Transaction transaction : transactions) {
			assertNotNull(transaction);
			assertEquals(Transaction.TransactionType.PAYMENT, transaction.getType());
			assertFalse(transaction.getFee().compareTo(BigDecimal.ZERO) == 0);
			assertNotNull(transaction.getReference());
			assertTrue(transaction.isSignatureValid());
		}

		// Attempt to load first transaction directly from database
		Transaction transaction = TransactionFactory.fromSignature(transactions.get(0).getSignature());
		assertNotNull(transaction);
		assertEquals(Transaction.TransactionType.PAYMENT, transaction.getType());
		assertFalse(transaction.getFee().compareTo(BigDecimal.ZERO) == 0);
		assertNotNull(transaction.getReference());
		assertTrue(transaction.isSignatureValid());
	}

	@Test
	public void testBlockSerialization() throws SQLException {
		// Block 949 has lots of varied transactions
		// Blocks 390 & 754 have only payment transactions
		Block block = Block.fromHeight(754);
		assertNotNull("Block 754 is required for this test", block);
		assertTrue(block.isSignatureValid());

		byte[] bytes = block.toBytes();

		assertEquals(block.getDataLength(), bytes.length);
	}

}
