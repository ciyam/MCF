package test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import data.block.BlockData;
import data.transaction.GenesisTransactionData;
import data.transaction.TransactionData;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import transform.TransformationException;
import transform.transaction.TransactionTransformer;

public class TransactionTests extends Common {

	@Test
	public void testGenesisSerialization() throws TransformationException, DataException {
		Repository repository = RepositoryManager.getRepository();
		GenesisBlock block = new GenesisBlock(repository);

		GenesisTransaction transaction = (GenesisTransaction) block.getTransactions().get(1);
		assertNotNull(transaction);

		GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transaction.getTransactionData();

		System.out.println(genesisTransactionData.getTimestamp() + ": " + genesisTransactionData.getRecipient() + " received "
				+ genesisTransactionData.getAmount().toPlainString());

		byte[] bytes = TransactionTransformer.toBytes(genesisTransactionData);

		GenesisTransactionData parsedTransactionData = (GenesisTransactionData) TransactionTransformer.fromBytes(bytes);

		System.out.println(parsedTransactionData.getTimestamp() + ": " + parsedTransactionData.getRecipient() + " received "
				+ parsedTransactionData.getAmount().toPlainString());

		assertTrue(Arrays.equals(genesisTransactionData.getSignature(), parsedTransactionData.getSignature()));
	}

	private void testGenericSerialization(TransactionData transactionData) throws TransformationException {
		assertNotNull(transactionData);

		byte[] bytes = TransactionTransformer.toBytes(transactionData);

		TransactionData parsedTransactionData = TransactionTransformer.fromBytes(bytes);

		assertTrue(Arrays.equals(transactionData.getSignature(), parsedTransactionData.getSignature()));

		assertEquals(TransactionTransformer.getDataLength(transactionData), bytes.length);
	}

	@Test
	public void testPaymentSerialization() throws TransformationException, DataException {
		Repository repository = RepositoryManager.getRepository();

		// Block 949 has lots of varied transactions
		// Blocks 390 & 754 have only payment transactions
		BlockData blockData = repository.getBlockRepository().fromHeight(754);
		assertNotNull("Block 754 is required for this test", blockData);

		Block block = new Block(repository, blockData);
		assertTrue(block.isSignatureValid());

		List<Transaction> transactions = block.getTransactions();
		assertNotNull(transactions);

		for (Transaction transaction : transactions)
			testGenericSerialization(transaction.getTransactionData());
	}

	@Test
	public void testMessageSerialization() throws SQLException, TransformationException {
		// Message transactions went live block 99000
		// Some transactions to be found in block 99001/2/5/6
	}

}