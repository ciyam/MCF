package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import qora.block.Block;
import qora.transaction.PaymentTransaction;
import utils.Base58;

public class navigation {

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
	public void testNavigateFromTransactionToBlock() throws SQLException {
		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		System.out.println("Navigating to Block from transaction " + signature58);

		PaymentTransaction paymentTransaction = PaymentTransaction.fromSignature(connection, signature);

		assertNotNull(paymentTransaction);

		Block block = paymentTransaction.getBlock(connection);

		assertNotNull(block);

		System.out.println("Block " + block.getHeight() + ", signature: " + Base58.encode(block.getSignature()));

		assertEquals(49778, block.getHeight());
	}

}
