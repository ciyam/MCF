package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import database.DB;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import utils.ParseException;

public class transactions extends common {

	@Test
	public void testGenesisSerialization() throws SQLException, ParseException {
		GenesisBlock block = GenesisBlock.getInstance();

		GenesisTransaction transaction = (GenesisTransaction) block.getTransactions().get(1);
		assertNotNull(transaction);
		System.out
				.println(transaction.getTimestamp() + ": " + transaction.getRecipient().getAddress() + " received " + transaction.getAmount().toPlainString());

		byte[] bytes = transaction.toBytes();

		GenesisTransaction parsedTransaction = (GenesisTransaction) Transaction.parse(bytes);
		System.out.println(parsedTransaction.getTimestamp() + ": " + parsedTransaction.getRecipient().getAddress() + " received "
				+ parsedTransaction.getAmount().toPlainString());

		assertTrue(Arrays.equals(transaction.getSignature(), parsedTransaction.getSignature()));
	}

	public void testGenericSerialization(Transaction transaction) throws SQLException, ParseException {
		assertNotNull(transaction);

		byte[] bytes = transaction.toBytes();

		Transaction parsedTransaction = Transaction.parse(bytes);

		assertTrue(Arrays.equals(transaction.getSignature(), parsedTransaction.getSignature()));
	}

	@Test
	public void testPaymentSerialization() throws SQLException, ParseException {
		try (final Connection connection = DB.getConnection()) {
			// Block 949 has lots of varied transactions
			// Blocks 390 & 754 have only payment transactions
			Block block = Block.fromHeight(754);
			assertNotNull("Block 754 is required for this test", block);
			assertTrue(block.isSignatureValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions)
				testGenericSerialization(transaction);
		}
	}

}