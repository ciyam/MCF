package test;

import java.sql.SQLException;

import org.junit.Test;

import database.DatabaseUpdates;

public class updates extends common {

	@Test
	public void testUpdates() throws SQLException {
		DatabaseUpdates.updateDatabase();
	}

}
