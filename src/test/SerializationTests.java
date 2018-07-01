package test;

import static org.junit.Assert.*;

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

public class SerializationTests extends Common {

	@Test
	public void testGenesisSerialization() throws TransformationException, DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
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

			/*
			 * NOTE: parsedTransactionData.getSignature() will be null as no signature is present in serialized bytes and calculating the signature is performed
			 * by GenesisTransaction, not GenesisTransactionData
			 */
			// Not applicable: assertTrue(Arrays.equals(genesisTransactionData.getSignature(), parsedTransactionData.getSignature()));

			GenesisTransaction parsedTransaction = new GenesisTransaction(repository, parsedTransactionData);
			assertTrue(Arrays.equals(genesisTransactionData.getSignature(), parsedTransaction.getTransactionData().getSignature()));
		}
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
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Block 949 has lots of varied transactions
			// Blocks 390 & 754 have only payment transactions
			BlockData blockData = repository.getBlockRepository().fromHeight(754);
			assertNotNull("Block 754 is required for this test", blockData);

			Block block = new Block(repository, blockData);

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions)
				testGenericSerialization(transaction.getTransactionData());
		}
	}

	@Test
	public void testMessageSerialization() throws TransformationException {
		// Message transactions went live block 99000
		// Some transactions to be found in block 99001/2/5/6
	}

	@Test
	public void testRegisterNameSerialization() throws TransformationException, DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Block 120 has only name registration transactions
			BlockData blockData = repository.getBlockRepository().fromHeight(120);
			assertNotNull("Block 120 is required for this test", blockData);

			Block block = new Block(repository, blockData);

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions)
				testGenericSerialization(transaction.getTransactionData());
		}
	}

	@Test
	public void testCreatePollSerialization() throws TransformationException, DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Block 10537 has only create poll transactions
			BlockData blockData = repository.getBlockRepository().fromHeight(10537);
			assertNotNull("Block 10537 is required for this test", blockData);

			Block block = new Block(repository, blockData);

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions)
				testGenericSerialization(transaction.getTransactionData());
		}
	}

}