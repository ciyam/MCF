package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Test;

public class connections {

	@Test
	public void testConnection() {
		try {
			Connection c = DriverManager.getConnection("jdbc:hsqldb:file:db/test", "SA", "");
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testSimultaneousConnections() {
		try {
			Connection c1 = DriverManager.getConnection("jdbc:hsqldb:file:db/test", "SA", "");
			Connection c2 = DriverManager.getConnection("jdbc:hsqldb:file:db/test", "SA", "");
			c1.close();
			c2.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testExistOnlyConnection() {
		try {
			Connection c = DriverManager.getConnection("jdbc:hsqldb:file:db/test;ifexists=true", "SA", "");
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testConnectionAfterShutdown() {
		try {
			Connection c = DriverManager.getConnection("jdbc:hsqldb:file:db/test", "SA", "");
			Statement s = c.createStatement();
			s.execute("SHUTDOWN COMPACT");
			c.close();

			c = DriverManager.getConnection("jdbc:hsqldb:file:db/test", "SA", "");
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testComplexConnection() {
		try {
			Connection c = DriverManager.getConnection("jdbc:hsqldb:file:db/test;create=false;close_result=true;sql.strict_exec=true;sql.enforce_names=true", "SA", "");
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

}
