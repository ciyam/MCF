package org.qora.test;

import org.junit.Test;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.repository.TransactionRepository;
import org.qora.test.common.Common;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.utils.Base58;

import static org.junit.Assert.*;

public class LoadTests extends Common {

	@Test
	public void testLoadPaymentTransaction() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository transactionRepository = repository.getTransactionRepository();

			assertTrue("Migrate from old database to at least block 49778 before running this test", repository.getBlockRepository().getBlockchainHeight() >= 49778);

			String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
			byte[] signature = Base58.decode(signature58);

			TransactionData transactionData = transactionRepository.fromSignature(signature);
			assertNotNull("Transaction data not loaded from repository", transactionData);
			assertEquals("Transaction data not PAYMENT type", TransactionType.PAYMENT, transactionData.getType());
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

			assertTrue("Migrate from old database to at least block 49778 before running this test", repository.getBlockRepository().getBlockchainHeight() >= 49778);

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
