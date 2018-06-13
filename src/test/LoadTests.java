package test;

import static org.junit.Assert.*;

import org.junit.Test;

import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.transaction.Transaction.TransactionType;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import repository.TransactionRepository;
import utils.Base58;

public class LoadTests extends Common {

	@Test
	public void testLoadPaymentTransaction() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		TransactionRepository transactionRepository = repository.getTransactionRepository();

		assertTrue("Migrate from old database to at least block 49778 before running this test",
				repository.getBlockRepository().getBlockchainHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		TransactionData transactionData = transactionRepository.fromSignature(signature);
		assertNotNull("Transaction data not loaded from repository", transactionData);
		assertEquals("Transaction data not PAYMENT type", TransactionType.PAYMENT, transactionData.getType().value);
		assertEquals(PublicKeyAccount.getAddress(transactionData.getCreatorPublicKey()), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");

		PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;

		assertNotNull(paymentTransactionData);
		assertEquals(PublicKeyAccount.getAddress(paymentTransactionData.getSenderPublicKey()), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");
		assertEquals(paymentTransactionData.getRecipient(), "QZsv8vbJ6QfrBNba4LMp5UtHhAzhrxvVUU");
		assertEquals(paymentTransactionData.getTimestamp(), 1416209264000L);
		assertEquals(Base58.encode(paymentTransactionData.getReference()),
				"31dC6kHHBeG5vYb8LMaZDjLEmhc9kQB2VUApVd8xWncSRiXu7yMejdprjYFMP2rUnzZxWd4KJhkq6LsV7rQvU1kY");
	}

	@Test
	public void testLoadFactory() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		TransactionRepository transactionRepository = repository.getTransactionRepository();

		assertTrue("Migrate from old database to at least block 49778 before running this test",
				repository.getBlockRepository().getBlockchainHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		while (true) {
			TransactionData transactionData = transactionRepository.fromSignature(signature);
			if (transactionData == null)
				break;

			PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;
			System.out.println(PublicKeyAccount.getAddress(paymentTransactionData.getSenderPublicKey()) + " sent " + paymentTransactionData.getAmount()
					+ " QORA to " + paymentTransactionData.getRecipient());

			signature = paymentTransactionData.getReference();
		}
	}

	@Test
	public void testLoadNonexistentTransaction() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		TransactionRepository transactionRepository = repository.getTransactionRepository();

		String signature58 = "1111222233334444";
		byte[] signature = Base58.decode(signature58);

		TransactionData transactionData = transactionRepository.fromSignature(signature);

		if (transactionData != null) {
			PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;

			System.out.println(PublicKeyAccount.getAddress(paymentTransactionData.getSenderPublicKey()) + " sent " + paymentTransactionData.getAmount()
					+ " QORA to " + paymentTransactionData.getRecipient());

			fail();
		}
	}

}
