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

import com.google.common.primitives.Bytes;

public class DB {

	public static void startTransaction(Connection c) throws SQLException {
		c.prepareStatement("START TRANSACTION").execute();
	}

	public static void commit(Connection c) throws SQLException {
		c.prepareStatement("COMMIT").execute();
	}

	public static void rollback(Connection c) throws SQLException {
		c.prepareStatement("ROLLBACK").execute();
	}

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

	public static byte[] getResultSetBytes(InputStream inputStream) {
		final int BYTE_BUFFER_LENGTH = 1024;

		if (inputStream == null)
			return null;

		byte[] result = new byte[0];

		while (true) {
			try {
				byte[] buffer = new byte[BYTE_BUFFER_LENGTH];
				int length = inputStream.read(buffer);
				result = Bytes.concat(result, Arrays.copyOf(buffer, length));
			} catch (IOException e) {
				// No more bytes
				break;
			}
		}

		return result;
	}

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

	public static void bindInsertPlaceholders(PreparedStatement preparedStatement, Object... objects) throws SQLException {
		// We need to bind two sets of placeholders based on this syntax:
		// INSERT INTO table (column, ... ) VALUES (?, ...) ON DUPLICATE KEY UPDATE SET column=?, ...
		for (int i = 0; i < objects.length; ++i) {
			Object object = objects[i];

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
	 * Execute SQL using byte[] as 1st placeholder
	 * <p>
	 * Typically used to fetch Blocks or Transactions using signature or reference.
	 * <p>
	 * 
	 * @param connection
	 * @param sql
	 * @param bytes
	 * @return ResultSet, or null if no matching rows found
	 * @throws SQLException
	 */
	public static ResultSet executeUsingBytes(Connection connection, String sql, byte[] bytes) throws SQLException {
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
