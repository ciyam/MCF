package database;

import java.sql.SQLException;

/**
 * Exception for use in DB-backed constructors to indicate no matching data found.
 * 
 */
@SuppressWarnings("serial")
public class NoDataFoundException extends SQLException {

	public NoDataFoundException() {
	}

}
