package repository.hsqldb;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import repository.AccountRepository;
import repository.AssetRepository;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import repository.TransactionRepository;
import repository.hsqldb.transaction.HSQLDBTransactionRepository;

public class HSQLDBRepository implements Repository {

	protected Connection connection;

	// NB: no visibility modifier so only callable from within same package
	HSQLDBRepository(Connection connection) {
		this.connection = connection;
	}

	@Override
	public AccountRepository getAccountRepository() {
		return new HSQLDBAccountRepository(this);
	}

	@Override
	public AssetRepository getAssetRepository() {
		return new HSQLDBAssetRepository(this);
	}

	@Override
	public BlockRepository getBlockRepository() {
		return new HSQLDBBlockRepository(this);
	}

	@Override
	public TransactionRepository getTransactionRepository() {
		return new HSQLDBTransactionRepository(this);
	}

	@Override
	public void saveChanges() throws DataException {
		try {
			this.connection.commit();
		} catch (SQLException e) {
			throw new DataException("commit error", e);
		}
	}

	@Override
	public void discardChanges() throws DataException {
		try {
			this.connection.rollback();
		} catch (SQLException e) {
			throw new DataException("rollback error", e);
		}
	}

	// TODO prevent leaking of connections if .close() is not called before garbage collection of the repository.
	// Maybe use PhantomReference to call .close() on connection after repository destruction?
	@Override
	public void close() throws DataException {
		try {
			// Diagnostic check for uncommitted changes
			Statement stmt = this.connection.createStatement();
			if (!stmt.execute("SELECT transaction, transaction_size FROM information_schema.system_sessions")) // TRANSACTION_SIZE() broken?
				throw new DataException("Unable to check repository status during close");

			ResultSet rs = stmt.getResultSet();
			if (rs == null || !rs.next())
				throw new DataException("Unable to check repository status during close");

			boolean inTransaction = rs.getBoolean(1);
			int transactionCount = rs.getInt(2);
			if (inTransaction && transactionCount != 0)
				System.out.println("Uncommitted changes (" + transactionCount + ") during repository close");

			// give connection back to the pool
			this.connection.close();
			this.connection = null;
		} catch (SQLException e) {
			throw new DataException("Error while closing repository", e);
		}
	}

	@Override
	public void rebuild() throws DataException {
	}

	/**
	 * Convert InputStream, from ResultSet.getBinaryStream(), into byte[].
	 * 
	 * @param inputStream
	 * @return byte[]
	 */
	public byte[] getResultSetBytes(InputStream inputStream) {
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
	public ResultSet checkedExecute(String sql, Object... objects) throws SQLException {
		PreparedStatement preparedStatement = this.connection.prepareStatement(sql);

		for (int i = 0; i < objects.length; ++i)
			// Special treatment for BigDecimals so that they retain their "scale",
			// which would otherwise be assumed as 0.
			if (objects[i] instanceof BigDecimal)
				preparedStatement.setBigDecimal(i + 1, (BigDecimal) objects[i]);
			else
				preparedStatement.setObject(i + 1, objects[i]);

		return this.checkedExecute(preparedStatement);
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
	public ResultSet checkedExecute(PreparedStatement preparedStatement) throws SQLException {
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
	public Long callIdentity() throws SQLException {
		PreparedStatement preparedStatement = this.connection.prepareStatement("CALL IDENTITY()");
		ResultSet resultSet = this.checkedExecute(preparedStatement);
		if (resultSet == null)
			return null;

		return resultSet.getLong(1);
	}

	/**
	 * Efficiently query database for existence of matching row.
	 * <p>
	 * {@code whereClause} is SQL "WHERE" clause containing "?" placeholders suitable for use with PreparedStatements.
	 * <p>
	 * Example call:
	 * <p>
	 * {@code String manufacturer = "Lamborghini";}<br>
	 * {@code int maxMileage = 100_000;}<br>
	 * {@code boolean isAvailable = exists("Cars", "manufacturer = ? AND mileage <= ?", manufacturer, maxMileage);}
	 * 
	 * @param tableName
	 * @param whereClause
	 * @param objects
	 * @return true if matching row found in database, false otherwise
	 * @throws SQLException
	 */
	public boolean exists(String tableName, String whereClause, Object... objects) throws SQLException {
		PreparedStatement preparedStatement = this.connection
				.prepareStatement("SELECT TRUE FROM " + tableName + " WHERE " + whereClause + " ORDER BY NULL LIMIT 1");
		ResultSet resultSet = this.checkedExecute(preparedStatement);
		if (resultSet == null)
			return false;

		return true;
	}

}
