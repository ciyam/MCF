package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import qora.transaction.PaymentTransaction;
import utils.Base58;

public class save {

	private static Connection connection;

	@Before
	public void connect() throws SQLException {
		connection = common.getConnection();
		Statement stmt = connection.createStatement();
		stmt.execute("SET DATABASE SQL SYNTAX MYS TRUE");
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
	public void testSavePaymentTransaction() throws SQLException {
		String reference58 = "rrrr";
		byte[] reference = Base58.decode(reference58);
		String signature58 = "ssss";
		byte[] signature = Base58.decode(signature58);

		PaymentTransaction paymentTransaction = new PaymentTransaction("Qsender", "Qrecipient", BigDecimal.valueOf(12345L), BigDecimal.ONE,
				Instant.now().getEpochSecond(), reference, signature);

		paymentTransaction.save(connection);
	}

}
