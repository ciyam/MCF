package test;

import java.sql.SQLException;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import database.DB;
import database.DatabaseUpdates;

public class common {

	@BeforeClass
	public static void setConnection() throws SQLException {
		DB.setUrl("jdbc:hsqldb:file:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true");
		DB.open();

		// Create/update database schema
		DatabaseUpdates.updateDatabase();
	}

	@AfterClass
	public static void closeDatabase() throws SQLException {
		DB.shutdown();
	}

}
