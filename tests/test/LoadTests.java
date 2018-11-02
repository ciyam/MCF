package test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository transactionRepository = repository.getTransactionRepository();

			assertTrue(repository.getBlockRepository().getBlockchainHeight() >= 49778,
				"Migrate from old database to at least block 49778 before running this test");

			String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
			byte[] signature = Base58.decode(signature58);

			TransactionData transactionData = transactionRepository.fromSignature(signature);
			assertNotNull(transactionData, "Transaction data not loaded from repository");
			assertEquals(TransactionType.PAYMENT, transactionData.getType(), "Transaction data not PAYMENT type");
			assertEquals("QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E", PublicKeyAccount.getAddress(transactionData.getCreatorPublicKey()));

			PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;

			assertNotNull(paymentTransactionData);
			assertEquals("QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E", PublicKeyAccount.getAddress(paymentTransactionData.getSenderPublicKey()));
			assertEquals("QZsv8vbJ6QfrBNba4LMp5UtHhAzhrxvVUU", paymentTransactionData.getRecipient());
			assertEquals(1416209264000L, paymentTransactionData.getTimestamp());
			assertEquals("31dC6kHHBeG5vYb8LMaZDjLEmhc9kQB2VUApVd8xWncSRiXu7yMejdprjYFMP2rUnzZxWd4KJhkq6LsV7rQvU1kY",
				Base58.encode(paymentTransactionData.getReference()));
		}
	}

	@Test
	public void testLoadFactory() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository transactionRepository = repository.getTransactionRepository();

			assertTrue(repository.getBlockRepository().getBlockchainHeight() >= 49778,
				"Migrate from old database to at least block 49778 before running this test");

			String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
			byte[] signature = Base58.decode(signature58);

			while (true) {
				TransactionData transactionData = transactionRepository.fromSignature(signature);
				if (transactionData == null)
					break;

				if (transactionData.getType() != TransactionType.PAYMENT)
					break;

				PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;
				System.out.println(PublicKeyAccount.getAddress(paymentTransactionData.getSenderPublicKey()) + " sent " + paymentTransactionData.getAmount()
						+ " QORA to " + paymentTransactionData.getRecipient());

				signature = transactionData.getReference();
			}
		}
	}

	@Test
	public void testLoadNonexistentTransaction() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
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

}
