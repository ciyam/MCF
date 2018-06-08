package database;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;

import org.hsqldb.jdbc.JDBCPool;

/**
 * Helper methods for common database actions.
 *
 */
public abstract class DB {

	private static JDBCPool connectionPool;
	private static String connectionUrl;
	private static ThreadLocal<Connection> local = new ThreadLocal<Connection>() {
		@Override
		protected Connection initialValue() {
			Connection conn = null;
			try {
				conn = connectionPool.getConnection();
			} catch (SQLException e) {
			}
			return conn;
		}
	};

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
	 * Return thread-local Connection from connection pool.
	 * <p>
	 * By default HSQLDB will wait up to 30 seconds for a pooled connection to become free.
	 * 
	 * @return Connection
	 */
	public static Connection getConnection() {
		return local.get();
	}

	public static void releaseConnection() {
		Connection connection = local.get();
		if (connection != null)
			try {
				connection.close();
			} catch (SQLException e) {
			}

		local.remove();
	}

	public static void startTransaction() throws SQLException {
		Connection connection = DB.getConnection();
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		connection.setAutoCommit(false);
	}

	public static void commit() throws SQLException {
		Connection connection = DB.getConnection();
		connection.commit();
		connection.setAutoCommit(true);
	}

	public static void rollback() throws SQLException {
		Connection connection = DB.getConnection();
		connection.rollback();
		connection.setAutoCommit(true);
	}

	public static Savepoint createSavepoint(String savepointName) throws SQLException {
		return DB.getConnection().setSavepoint(savepointName);
	}

	public static void rollbackToSavepoint(Savepoint savepoint) throws SQLException {
		DB.getConnection().rollback(savepoint);
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
	public static void shutdown() throws SQLException {
		DB.getConnection().createStatement().execute("SHUTDOWN");
		DB.releaseConnection();
		connectionPool.close(0);
	}

	/**
	 * Shutdown and delete database, then rebuild it.
	 * <p>
	 * See {@link DB#shutdown()} for warnings about connections.
	 * <p>
	 * Note that this only rebuilds the database schema, not the data itself.
	 * 
	 * @throws SQLException
	 */
	public static void rebuild() throws SQLException {
		// Shutdown database and close any access
		DB.shutdown();

		// Wipe files (if any)
		// TODO

		// Re-open clean database
		DB.open();

		// Apply schema updates
		DatabaseUpdates.updateDatabase();
	}

	/**
	 * Convert InputStream, from ResultSet.getBinaryStream(), into byte[].
	 * 
	 * @param inputStream
	 * @return byte[]
	 */
	public static byte[] getResultSetBytes(InputStream inputStream) {
		// inputStream could be null if database's column's value is null
		if (inputStream == null)
			return null;

		try {
			int length = inputStream.available();
			byte[] result = new byte[length];

			if (inputStream.read(result) == length)
				return result;
		} catch (IOException e) {
			// Fall-through to return null
		}

		return null;
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
		PreparedStatement preparedStatement = DB.getConnection().prepareStatement(sql);

		for (int i = 0; i < objects.length; ++i)
			// Special treatment for BigDecimals so that they retain their "scale",
			// which would otherwise be assumed as 0.
			if (objects[i] instanceof BigDecimal)
				preparedStatement.setBigDecimal(i + 1, (BigDecimal) objects[i]);
			else
				preparedStatement.setObject(i + 1, objects[i]);

		return DB.checkedExecute(preparedStatement);
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
	 * @return Long
	 * @throws SQLException
	 */
	public static Long callIdentity() throws SQLException {
		PreparedStatement preparedStatement = DB.getConnection().prepareStatement("CALL IDENTITY()");
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
		PreparedStatement preparedStatement = DB.getConnection()
				.prepareStatement("SELECT TRUE FROM " + tableName + " WHERE " + whereClause + " ORDER BY NULL LIMIT 1");
		ResultSet resultSet = DB.checkedExecute(preparedStatement);
		if (resultSet == null)
			return false;

		return true;
	}

}
