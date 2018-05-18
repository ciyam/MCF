package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import utils.Base58;

public class load {

	private static Connection connection;

	@Before
	public void connect() throws SQLException {
		connection = common.getConnection();
	}

	@After
	public void disconnect() {
		try {
			connection.createStatement().execute("SHUTDOWN");
		} catch (SQLException e) {
			fail();
		}
	}

	@Test
	public void testLoadPaymentTransaction() throws SQLException {
		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		PaymentTransaction paymentTransaction = PaymentTransaction.fromSignature(connection, signature);

		assertEquals(paymentTransaction.getSender().getAddress(), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");
		assertEquals(paymentTransaction.getCreator().getAddress(), "QXwu8924WdgPoRmtiWQBUMF6eedmp1Hu2E");
		assertEquals(paymentTransaction.getRecipient().getAddress(), "QZsv8vbJ6QfrBNba4LMp5UtHhAzhrxvVUU");
		assertEquals(paymentTransaction.getTimestamp(), 1416209264000L);
		assertEquals(Base58.encode(paymentTransaction.getReference()),
				"31dC6kHHBeG5vYb8LMaZDjLEmhc9kQB2VUApVd8xWncSRiXu7yMejdprjYFMP2rUnzZxWd4KJhkq6LsV7rQvU1kY");
	}

	@Test
	public void testLoadFactory() throws SQLException {
		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		while (true) {
			Transaction transaction = TransactionFactory.fromSignature(connection, signature);
			if (transaction == null)
				break;

			PaymentTransaction payment = (PaymentTransaction) transaction;
			System.out.println("Transaction " + Base58.encode(payment.getSignature()) + ": " + payment.getAmount().toString() + " QORA from "
					+ payment.getSender().getAddress() + " to " + payment.getRecipient());

			signature = payment.getReference();
		}
	}

	@Test
	public void testLoadNonexistentTransaction() throws SQLException {
		String signature58 = "1111222233334444";
		byte[] signature = Base58.decode(signature58);

		PaymentTransaction payment = PaymentTransaction.fromSignature(connection, signature);

		if (payment != null) {
			System.out.println("Transaction " + Base58.encode(payment.getSignature()) + ": " + payment.getAmount().toString() + " QORA from "
					+ payment.getSender().getAddress() + " to " + payment.getRecipient());
			fail();
		}
	}

}
