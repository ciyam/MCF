package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import qora.block.GenesisBlock;
import utils.Base58;

public class signatures {

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
	public void testGenesisBlockSignature() throws SQLException {
		String expected58 = "6pHMBFif7jXFG654joT8GPaymau1fMtaxacRyqSrnAwQMQDvqRuLpHpfFyqX4gWVvj4pF1mwQhFgqWAvjVvPJUjmBZQvL751dM9cEcQBTaUcxtNLuWZCVUAtbnWN9f7FsLppHhkPbxwpoodL3UJYRGt3EZrG17mhv1RJbmq8j6rr7Mk";

		GenesisBlock block = GenesisBlock.getInstance();

		System.out.println("Generator: " + block.getGenerator().getAddress() + ", generation signature: " + Base58.encode(block.getGenerationSignature()));

		assertEquals(expected58, Base58.encode(block.getSignature()));
	}

}
