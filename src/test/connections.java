package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Test;

import database.DB;

public class connections extends common {

	@Test
	public void testConnection() {
		Connection connection = DB.getConnection();
		assertNotNull(connection);
	}

	@Test
	public void testSimultaneousConnections() {
		// First connection is the thread-local one
		Connection connection = DB.getConnection();
		assertNotNull(connection);

		int n_connections = 5;
		Connection[] connections = new Connection[n_connections];

		for (int i = 0; i < n_connections; ++i) {
			connections[i] = DB.getConnection();
			assertEquals(connection, connections[i]);
		}
	}

	@Test
	public void testConnectionAfterShutdown() {
		try {
			DB.shutdown();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}

		try {
			DB.open();
			Connection connection = DB.getConnection();
			assertNotNull(connection);
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

}
