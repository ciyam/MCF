package test;

import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class common {

	public static Connection getConnection() {
		try {
			return DriverManager.getConnection("jdbc:hsqldb:file:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true", "SA", "");
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
			return null;
		}
	}

}
