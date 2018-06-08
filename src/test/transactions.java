package test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.GenesisTransaction;
import qora.transaction.TransactionHandler;
import transform.TransformationException;

public class transactions extends common {

	@Test
	public void testGenesisSerialization() throws SQLException, TransformationException {
		GenesisBlock block = GenesisBlock.getInstance();

		GenesisTransaction transaction = (GenesisTransaction) block.getTransactions().get(1);
		assertNotNull(transaction);
		System.out
				.println(transaction.getTimestamp() + ": " + transaction.getRecipient().getAddress() + " received " + transaction.getAmount().toPlainString());

		byte[] bytes = transaction.toBytes();

		GenesisTransaction parsedTransaction = (GenesisTransaction) TransactionHandler.parse(bytes);
		System.out.println(parsedTransaction.getTimestamp() + ": " + parsedTransaction.getRecipient().getAddress() + " received "
				+ parsedTransaction.getAmount().toPlainString());

		assertTrue(Arrays.equals(transaction.getSignature(), parsedTransaction.getSignature()));
	}

	public void testGenericSerialization(TransactionHandler transaction) throws SQLException, TransformationException {
		assertNotNull(transaction);

		byte[] bytes = transaction.toBytes();

		TransactionHandler parsedTransaction = TransactionHandler.parse(bytes);

		assertTrue(Arrays.equals(transaction.getSignature(), parsedTransaction.getSignature()));

		assertEquals(transaction.getDataLength(), bytes.length);
	}

	@Test
	public void testPaymentSerialization() throws SQLException, TransformationException {
		// Block 949 has lots of varied transactions
		// Blocks 390 & 754 have only payment transactions
		Block block = Block.fromHeight(754);
		assertNotNull("Block 754 is required for this test", block);
		assertTrue(block.isSignatureValid());

		List<TransactionHandler> transactions = block.getTransactions();
		assertNotNull(transactions);

		for (TransactionHandler transaction : transactions)
			testGenericSerialization(transaction);
	}

	@Test
	public void testMessageSerialization() throws SQLException, TransformationException {
		// Message transactions went live block 99000
		// Some transactions to be found in block 99001/2/5/6
	}

}