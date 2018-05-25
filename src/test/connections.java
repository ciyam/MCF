package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Test;

import database.DB;

public class connections extends common {

	@Test
	public void testConnection() {
		try {
			Connection c = DB.getConnection();
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testSimultaneousConnections() {
		int n_connections = 5;
		Connection[] connections = new Connection[n_connections];

		try {
			for (int i = 0; i < n_connections; ++i)
				connections[i] = DB.getConnection();

			// Close in same order as opening
			for (int i = 0; i < n_connections; ++i)
				connections[i].close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testConnectionAfterShutdown() {
		try {
			DB.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}

		try {
			DB.open();
			Connection c = DB.getConnection();
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

}
