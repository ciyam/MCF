package test;

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.Test;

import qora.block.BlockChain;
import qora.transaction.PaymentTransaction;
import qora.transaction.TransactionHandler;
import qora.transaction.TransactionHandler;
import utils.Base58;

public class load extends common {

	@Test
	public void testLoadPaymentTransaction() throws SQLException {
		assertTrue("Migrate old database to at least block 49778 before running this test", BlockChain.getHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		PaymentTransaction paymentTransaction = PaymentTransaction.fromSignature(signature);

		assertNotNull(paymentTransaction);
		assertEquals(paymentTransaction.getSender().getAddress(), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");
		assertEquals(paymentTransaction.getCreator().getAddress(), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");
		assertEquals(paymentTransaction.getRecipient().getAddress(), "QZsv8vbJ6QfrBNba4LMp5UtHhAzhrxvVUU");
		assertEquals(paymentTransaction.getTimestamp(), 1416209264000L);
		assertEquals(Base58.encode(paymentTransaction.getReference()),
				"31dC6kHHBeG5vYb8LMaZDjLEmhc9kQB2VUApVd8xWncSRiXu7yMejdprjYFMP2rUnzZxWd4KJhkq6LsV7rQvU1kY");
	}

	@Test
	public void testLoadFactory() throws SQLException {
		assertTrue("Migrate old database to at least block 49778 before running this test", BlockChain.getHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		while (true) {
			TransactionHandler transaction = TransactionFactory.fromSignature(signature);
			if (transaction == null)
				break;

			PaymentTransaction payment = (PaymentTransaction) transaction;
			System.out
					.println(payment.getSender().getAddress() + " sent " + payment.getAmount().toString() + " QORA to " + payment.getRecipient().getAddress());

			signature = payment.getReference();
		}
	}

	@Test
	public void testLoadNonexistentTransaction() throws SQLException {
		String signature58 = "1111222233334444";
		byte[] signature = Base58.decode(signature58);

		PaymentTransaction payment = PaymentTransaction.fromSignature(signature);

		if (payment != null) {
			System.out
					.println(payment.getSender().getAddress() + " sent " + payment.getAmount().toString() + " QORA to " + payment.getRecipient().getAddress());
			fail();
		}
	}

}
