package database;

import java.io.ByteArrayInputStream;
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

	public static void open() throws SQLException {
		connectionPool = new JDBCPool();
		connectionPool.setUrl(connectionUrl);
	}

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

	public static void close() throws SQLException {
		getConnection().createStatement().execute("SHUTDOWN");
		connectionPool.close(0);
	}

	/**
	 * Convert InputStream, from ResultSet.getBinaryStream(), into byte[] of set length.
	 * 
	 * @param inputStream
	 * @param length
	 * @return byte[length]
	 */
	public static byte[] getResultSetBytes(InputStream inputStream, int length) {
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
	 * Format table and column names into an INSERT INTO ... SQL statement.
	 * <p>
	 * Full form is:
	 * <p>
	 * INSERT INTO <I>table</I> (<I>column</I>, ...) VALUES (?, ...) ON DUPLICATE KEY UPDATE <I>column</I>=?, ...
	 * <p>
	 * Note that HSQLDB needs to put into mySQL compatibility mode first via "SET DATABASE SQL SYNTAX MYS TRUE".
	 * 
	 * @param table
	 * @param columns
	 * @return String
	 */
	public static String formatInsertWithPlaceholders(String table, String... columns) {
		String[] placeholders = new String[columns.length];
		Arrays.setAll(placeholders, (int i) -> "?");

		StringBuilder output = new StringBuilder();
		output.append("INSERT INTO ");
		output.append(table);
		output.append(" (");
		output.append(String.join(", ", columns));
		output.append(") VALUES (");
		output.append(String.join(", ", placeholders));
		output.append(") ON DUPLICATE KEY UPDATE ");
		output.append(String.join("=?, ", columns));
		output.append("=?");
		return output.toString();
	}

	/**
	 * Binds Objects to PreparedStatement based on INSERT INTO ... ON DUPLICATE KEY UPDATE ...
	 * <p>
	 * Note that each object is bound to <b>two</b> place-holders based on this SQL syntax:
	 * <p>
	 * INSERT INTO <I>table</I> (<I>column</I>, ...) VALUES (<b>?</b>, ...) ON DUPLICATE KEY UPDATE <I>column</I>=<b>?</b>, ...
	 * <p>
	 * Requires that mySQL SQL syntax support is enabled during connection.
	 * 
	 * @param preparedStatement
	 * @param objects
	 * @throws SQLException
	 */
	public static void bindInsertPlaceholders(PreparedStatement preparedStatement, Object... objects) throws SQLException {
		for (int i = 0; i < objects.length; ++i) {
			Object object = objects[i];

			// Special treatment for BigDecimals so that they retain their "scale",
			// which would otherwise be assumed as 0.
			if (object instanceof BigDecimal) {
				preparedStatement.setBigDecimal(i + 1, (BigDecimal) object);
				preparedStatement.setBigDecimal(i + objects.length + 1, (BigDecimal) object);
			} else {
				preparedStatement.setObject(i + 1, object);
				preparedStatement.setObject(i + objects.length + 1, object);
			}
		}
	}

	/**
	 * Execute SQL using byte[] as 1st placeholder.
	 * <p>
	 * <b>Note: calls ResultSet.next()</b> therefore returned ResultSet is already pointing to first row.
	 * <p>
	 * Typically used to fetch Blocks or Transactions using signature or reference.
	 * 
	 * @param sql
	 * @param bytes
	 * @return ResultSet, or null if no matching rows found
	 * @throws SQLException
	 */
	public static ResultSet executeUsingBytes(String sql, byte[] bytes) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			PreparedStatement preparedStatement = connection.prepareStatement(sql);
			preparedStatement.setBinaryStream(1, new ByteArrayInputStream(bytes));

			if (!preparedStatement.execute())
				throw new SQLException("Fetching from database produced no results");

			ResultSet rs = preparedStatement.getResultSet();
			if (rs == null)
				throw new SQLException("Fetching results from database produced no ResultSet");

			if (!rs.next())
				return null;

			return rs;
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

}
