package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import data.transaction.TransactionData;
import qora.account.Account;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.Transaction;
import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;

// Don't extend Common as we want an in-memory database
public class GenesisTests {

	public static final String connectionUrl = "jdbc:hsqldb:mem:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true";

	@BeforeClass
	public static void setRepository() throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);
	}

	@AfterClass
	public static void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

	@Test
	public void testGenesisBlockTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());

			GenesisBlock block = new GenesisBlock(repository);

			assertNotNull(block);
			assertTrue(block.isSignatureValid());
			// Note: only true if blockchain is empty
			assertEquals(Block.ValidationResult.OK, block.isValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

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

			// Check known balance
			Account testAccount = new Account(repository, "QegT2Ws5YjLQzEZ9YMzWsAZMBE8cAygHZN");
			BigDecimal testBalance = testAccount.getConfirmedBalance(Asset.QORA);
			BigDecimal expectedBalance = new BigDecimal("12606834").setScale(8);
			assertTrue(testBalance.compareTo(expectedBalance) == 0);
		}
	}

}
