package database;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.hsqldb.jdbc.JDBCPool;

import com.google.common.primitives.Bytes;

/**
 * Helper methods for common database actions.
 *
 */
public class DB {

	private static JDBCPool connectionPool;
	private static String connectionUrl;

	/**
	 * Open connection pool to database using prior set connection URL.
	 * <p>
	 * The connection URL <b>must</b> be set via {@link DB#setUrl(String)} before using this call.
	 * 
	 * @throws SQLException
	 * @see DB#setUrl(String)
	 */
	public static void open() throws SQLException {
		connectionPool = new JDBCPool();
		connectionPool.setUrl(connectionUrl);
	}

	/**
	 * Set the database connection URL.
	 * <p>
	 * Typical example:
	 * <p>
	 * {@code setUrl("jdbc:hsqldb:file:db/qora")}
	 * 
	 * @param url
	 */
	public static void setUrl(String url) {
		connectionUrl = url;
	}

	/**
	 * Return an on-demand Connection from connection pool.
	 * <p>
	 * Mostly used in database-read scenarios whereas database-write scenarios, especially multi-statement transactions, are likely to pass around a Connection
	 * object.
	 * <p>
	 * By default HSQLDB will wait up to 30 seconds for a pooled connection to become free.
	 * 
	 * @return Connection
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
		return connectionPool.getConnection();
	}

	public static void startTransaction(Connection c) throws SQLException {
		c.prepareStatement("START TRANSACTION").execute();
	}

	public static void commit(Connection c) throws SQLException {
		c.prepareStatement("COMMIT").execute();
	}

	public static void rollback(Connection c) throws SQLException {
		c.prepareStatement("ROLLBACK").execute();
	}

	public static void createSavepoint(Connection c, String savepointName) throws SQLException {
		c.prepareStatement("SAVEPOINT " + savepointName).execute();
	}

	public static void rollbackToSavepoint(Connection c, String savepointName) throws SQLException {
		c.prepareStatement("ROLLBACK TO SAVEPOINT " + savepointName).execute();
	}

	/**
	 * Shutdown database and close all connections in connection pool.
	 * <p>
	 * Note: any attempts to use an existing connection after this point will fail. Also, any attempts to request a connection using {@link DB#getConnection()}
	 * will fail.
	 * <p>
	 * After this method returns, the database <i>can</i> be reopened using {@link DB#open()}.
	 * 
	 * @throws SQLException
	 */
	public static void close() throws SQLException {
		getConnection().createStatement().execute("SHUTDOWN");
		connectionPool.close(0);
	}

	/**
	 * Shutdown and delete database, then rebuild it.
	 * <p>
	 * See {@link DB#close()} for warnings about connections.
	 * <p>
	 * Note that this only rebuilds the database schema, not the data itself.
	 * 
	 * @throws SQLException
	 */
	public static void rebuild() throws SQLException {
		// Shutdown database and close any access
		DB.close();

		// Wipe files (if any)
		// TODO

		// Re-open clean database
		DB.open();

		// Apply schema updates
		DatabaseUpdates.updateDatabase();
	}

	/**
	 * Convert InputStream, from ResultSet.getBinaryStream(), into byte[] of set length.
	 * 
	 * @param inputStream
	 * @param length
	 * @return byte[length]
	 */
	public static byte[] getResultSetBytes(InputStream inputStream, int length) {
		// inputStream could be null if database's column's value is null
		if (inputStream == null)
			return null;

		byte[] result = new byte[length];

		try {
			if (inputStream.read(result) == length)
				return result;
		} catch (IOException e) {
			// Fall-through to return null
		}

		return null;
	}

	/**
	 * Convert InputStream, from ResultSet.getBinaryStream(), into byte[] of unknown length.
	 * 
	 * @param inputStream
	 * @return byte[]
	 */
	public static byte[] getResultSetBytes(InputStream inputStream) {
		final int BYTE_BUFFER_LENGTH = 1024;

		// inputStream could be null if database's column's value is null
		if (inputStream == null)
			return null;

		byte[] result = new byte[0];

		while (true) {
			try {
				byte[] buffer = new byte[BYTE_BUFFER_LENGTH];
				int length = inputStream.read(buffer);
				if (length == -1)
					break;

				result = Bytes.concat(result, Arrays.copyOf(buffer, length));
			} catch (IOException e) {
				// No more bytes
				break;
			}
		}

		return result;
	}

	/**
	 * Execute SQL and return ResultSet with but added checking.
	 * <p>
	 * <b>Note: calls ResultSet.next()</b> therefore returned ResultSet is already pointing to first row.
	 * 
	 * @param sql
	 * @param objects
	 * @return ResultSet, or null if there are no found rows
	 * @throws SQLException
	 */
	public static ResultSet checkedExecute(String sql, Object... objects) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			PreparedStatement preparedStatement = connection.prepareStatement(sql);
			for (int i = 0; i < objects.length; ++i)
				// Special treatment for BigDecimals so that they retain their "scale",
				// which would otherwise be assumed as 0.
				if (objects[i] instanceof BigDecimal)
					preparedStatement.setBigDecimal(i + 1, (BigDecimal) objects[i]);
				else
					preparedStatement.setObject(i + 1, objects[i]);

			return checkedExecute(preparedStatement);
		}
	}

	/**
	 * Execute PreparedStatement and return ResultSet with but added checking.
	 * <p>
	 * <b>Note: calls ResultSet.next()</b> therefore returned ResultSet is already pointing to first row.
	 * 
	 * @param preparedStatement
	 * @return ResultSet, or null if there are no found rows
	 * @throws SQLException
	 */
	public static ResultSet checkedExecute(PreparedStatement preparedStatement) throws SQLException {
		if (!preparedStatement.execute())
			throw new SQLException("Fetching from database produced no results");

		ResultSet resultSet = preparedStatement.getResultSet();
		if (resultSet == null)
			throw new SQLException("Fetching results from database produced no ResultSet");

		if (!resultSet.next())
			return null;

		return resultSet;
	}

	/**
	 * Fetch last value of IDENTITY column after an INSERT statement.
	 * <p>
	 * Performs "CALL IDENTITY()" SQL statement to retrieve last value used when INSERTing into a table that has an IDENTITY column.
	 * <p>
	 * Typically used after INSERTing NULL as the IDENTIY column's value to fetch what value was actually stored by HSQLDB.
	 * 
	 * @param connection
	 * @return Long
	 * @throws SQLException
	 */
	public static Long callIdentity(Connection connection) throws SQLException {
		PreparedStatement preparedStatement = connection.prepareStatement("CALL IDENTITY()");
		ResultSet resultSet = DB.checkedExecute(preparedStatement);
		if (resultSet == null)
			return null;

		return resultSet.getLong(1);
	}

	/**
	 * Efficiently query database for existing of matching row.
	 * <p>
	 * {@code whereClause} is SQL "WHERE" clause containing "?" placeholders suitable for use with PreparedStatements.
	 * <p>
	 * Example call:
	 * <p>
	 * {@code String manufacturer = "Lamborghini";}<br>
	 * {@code int maxMileage = 100_000;}<br>
	 * {@code boolean isAvailable = DB.exists("Cars", "manufacturer = ? AND mileage <= ?", manufacturer, maxMileage);}
	 * 
	 * @param tableName
	 * @param whereClause
	 * @param objects
	 * @return true if matching row found in database, false otherwise
	 * @throws SQLException
	 */
	public static boolean exists(String tableName, String whereClause, Object... objects) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			PreparedStatement preparedStatement = connection
					.prepareStatement("SELECT TRUE FROM " + tableName + " WHERE " + whereClause + " ORDER BY NULL LIMIT 1");
			ResultSet resultSet = DB.checkedExecute(preparedStatement);
			if (resultSet == null)
				return false;

			return true;
		}
	}

}
