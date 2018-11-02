package test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import data.block.BlockData;
import data.transaction.GenesisTransactionData;
import data.transaction.TransactionData;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import qora.transaction.Transaction.TransactionType;
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

		assertTrue(Arrays.equals(transactionData.getSignature(), parsedTransactionData.getSignature()), "Transaction signature mismatch");

		assertEquals(bytes.length, TransactionTransformer.getDataLength(transactionData), "Data length mismatch");
	}

	private void testSpecificBlockTransactions(int height, TransactionType type) throws DataException, TransformationException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromHeight(height);
			assertNotNull(blockData, "Block " + height + " is required for this test");

			Block block = new Block(repository, blockData);

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			for (Transaction transaction : transactions)
				if (transaction.getTransactionData().getType() == type)
					testGenericSerialization(transaction.getTransactionData());
		}
	}

	@Test
	public void testPaymentSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(754, TransactionType.PAYMENT);
	}

	@Test
	public void testRegisterNameSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(120, TransactionType.REGISTER_NAME);
	}

	@Test
	public void testUpdateNameSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(673, TransactionType.UPDATE_NAME);
	}

	@Test
	public void testSellNameSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(200, TransactionType.SELL_NAME);
	}

	@Test
	public void testCancelSellNameSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(741, TransactionType.CANCEL_SELL_NAME);
	}

	@Test
	public void testBuyNameSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(973, TransactionType.BUY_NAME);
	}

	@Test
	public void testCreatePollSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(10537, TransactionType.CREATE_POLL);
	}

	@Test
	public void testVoteOnPollSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(10540, TransactionType.CREATE_POLL);
	}

	@Test
	public void testIssueAssetSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(33661, TransactionType.ISSUE_ASSET);
	}

	@Test
	public void testTransferAssetSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(39039, TransactionType.TRANSFER_ASSET);
	}

	@Test
	public void testCreateAssetOrderSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(35611, TransactionType.CREATE_ASSET_ORDER);
	}

	@Test
	public void testCancelAssetOrderSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(36176, TransactionType.CANCEL_ASSET_ORDER);
	}

	@Test
	public void testMultiPaymentSerialization() throws TransformationException, DataException {
		testSpecificBlockTransactions(34500, TransactionType.MULTIPAYMENT);
	}

	@Test
	public void testMessageSerialization() throws TransformationException, DataException {
		// Message transactions went live block 99000
		// Some transactions to be found in block 99001/2/5/6
		testSpecificBlockTransactions(99001, TransactionType.MESSAGE);
	}

}