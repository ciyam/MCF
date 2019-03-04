package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.repository.ATRepository;
import org.qora.repository.AccountRepository;
import org.qora.repository.AssetRepository;
import org.qora.repository.BlockRepository;
import org.qora.repository.GroupRepository;
import org.qora.repository.DataException;
import org.qora.repository.NameRepository;
import org.qora.repository.NetworkRepository;
import org.qora.repository.Repository;
import org.qora.repository.TransactionRepository;
import org.qora.repository.VotingRepository;
import org.qora.repository.hsqldb.transaction.HSQLDBTransactionRepository;

public class HSQLDBRepository implements Repository {

	/** Queries that take longer than this (milliseconds) are logged */
	private static final long MAX_QUERY_TIME = 1000L;

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBRepository.class);

	public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	protected Connection connection;
	protected Deque<Savepoint> savepoints;
	protected boolean debugState = false;

	// NB: no visibility modifier so only callable from within same package
	HSQLDBRepository(Connection connection) {
		this.connection = connection;
		this.savepoints = new ArrayDeque<>(3);
	}

	@Override
	public ATRepository getATRepository() {
		return new HSQLDBATRepository(this);
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
	public GroupRepository getGroupRepository() {
		return new HSQLDBGroupRepository(this);
	}

	@Override
	public NameRepository getNameRepository() {
		return new HSQLDBNameRepository(this);
	}

	@Override
	public NetworkRepository getNetworkRepository() {
		return new HSQLDBNetworkRepository(this);
	}

	@Override
	public TransactionRepository getTransactionRepository() {
		return new HSQLDBTransactionRepository(this);
	}

	@Override
	public VotingRepository getVotingRepository() {
		return new HSQLDBVotingRepository(this);
	}

	@Override
	public void saveChanges() throws DataException {
		try {
			this.connection.commit();
		} catch (SQLException e) {
			throw new DataException("commit error", e);
		} finally {
			this.savepoints.clear();
		}
	}

	@Override
	public void discardChanges() throws DataException {
		try {
			this.connection.rollback();
		} catch (SQLException e) {
			throw new DataException("rollback error", e);
		} finally {
			this.savepoints.clear();
		}
	}

	@Override
	public void setSavepoint() throws DataException {
		try {
			Savepoint savepoint = this.connection.setSavepoint();
			this.savepoints.push(savepoint);
		} catch (SQLException e) {
			throw new DataException("savepoint error", e);
		}
	}

	@Override
	public void rollbackToSavepoint() throws DataException {
		if (this.savepoints.isEmpty())
			throw new DataException("no savepoint to rollback");

		Savepoint savepoint = this.savepoints.pop();

		try {
			this.connection.rollback(savepoint);
		} catch (SQLException e) {
			throw new DataException("savepoint rollback error", e);
		}
	}

	@Override
	public void close() throws DataException {
		// Already closed? No need to do anything but maybe report double-call
		if (this.connection == null) {
			LOGGER.warn("HSQLDBRepository.close() called when repository already closed", new Exception("Repository already closed"));
			return;
		}

		try (Statement stmt = this.connection.createStatement()) {
			// Diagnostic check for uncommitted changes
			if (!stmt.execute("SELECT transaction, transaction_size FROM information_schema.system_sessions")) // TRANSACTION_SIZE() broken?
				throw new DataException("Unable to check repository status during close");

			try (ResultSet resultSet = stmt.getResultSet()) {
				if (resultSet == null || !resultSet.next())
					LOGGER.warn("Unable to check repository status during close");

				boolean inTransaction = resultSet.getBoolean(1);
				int transactionCount = resultSet.getInt(2);
				if (inTransaction && transactionCount != 0)
					LOGGER.warn("Uncommitted changes (" + transactionCount + ") during repository close", new Exception("Uncommitted repository changes"));
			}

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

	@Override
	public void setDebug(boolean debugState) {
		this.debugState = debugState;
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
	@SuppressWarnings("resource")
	public ResultSet checkedExecute(String sql, Object... objects) throws SQLException {
		if (this.debugState)
			LOGGER.debug(sql);

		PreparedStatement preparedStatement = this.connection.prepareStatement(sql);

		// Close the PreparedStatement when the ResultSet is closed otherwise there's a potential resource leak.
		// We can't use try-with-resources here as closing the PreparedStatement on return would also prematurely close the ResultSet.
		preparedStatement.closeOnCompletion();

		long beforeQuery = System.currentTimeMillis();

		ResultSet resultSet = this.checkedExecuteResultSet(preparedStatement, objects);

		long queryTime = System.currentTimeMillis() - beforeQuery;
		if (queryTime > MAX_QUERY_TIME)
			LOGGER.info(String.format("HSQLDB query took %d ms: %s", queryTime, sql));

		return resultSet;
	}

	/**
	 * Bind objects to placeholders in prepared statement.
	 * <p>
	 * Special treatment for BigDecimals so that they retain their "scale".
	 * 
	 * @param preparedStatement
	 * @param objects
	 * @throws SQLException
	 */
	private void prepareExecute(PreparedStatement preparedStatement, Object... objects) throws SQLException {
		for (int i = 0; i < objects.length; ++i)
			// Special treatment for BigDecimals so that they retain their "scale",
			// which would otherwise be assumed as 0.
			if (objects[i] instanceof BigDecimal)
				preparedStatement.setBigDecimal(i + 1, (BigDecimal) objects[i]);
			else
				preparedStatement.setObject(i + 1, objects[i]);
	}

	/**
	 * Execute PreparedStatement and return ResultSet with but added checking.
	 * <p>
	 * <b>Note: calls ResultSet.next()</b> therefore returned ResultSet is already pointing to first row.
	 * 
	 * @param preparedStatement
	 * @param objects
	 * @return ResultSet, or null if there are no found rows
	 * @throws SQLException
	 */
	private ResultSet checkedExecuteResultSet(PreparedStatement preparedStatement, Object... objects) throws SQLException {
		prepareExecute(preparedStatement, objects);

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
	 * Execute PreparedStatement and return changed row count.
	 * 
	 * @param preparedStatement
	 * @param objects
	 * @return number of changed rows
	 * @throws SQLException
	 */
	private int checkedExecuteUpdateCount(PreparedStatement preparedStatement, Object... objects) throws SQLException {
		prepareExecute(preparedStatement, objects);

		if (preparedStatement.execute())
			throw new SQLException("Database produced results, not row count");

		int rowCount = preparedStatement.getUpdateCount();
		if (rowCount == -1)
			throw new SQLException("Database returned invalid row count");

		return rowCount;
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
		try (PreparedStatement preparedStatement = this.connection.prepareStatement("CALL IDENTITY()");
				ResultSet resultSet = this.checkedExecuteResultSet(preparedStatement)) {
			if (resultSet == null)
				return null;

			return resultSet.getLong(1);
		}
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
		try (PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT TRUE FROM " + tableName + " WHERE " + whereClause + " LIMIT 1");
				ResultSet resultSet = this.checkedExecuteResultSet(preparedStatement, objects)) {
			if (resultSet == null)
				return false;

			return true;
		}
	}

	/**
	 * Delete rows from database table.
	 * 
	 * @param tableName
	 * @param whereClause
	 * @param objects
	 * @throws SQLException
	 */
	public int delete(String tableName, String whereClause, Object... objects) throws SQLException {
		try (PreparedStatement preparedStatement = this.connection.prepareStatement("DELETE FROM " + tableName + " WHERE " + whereClause)) {
			return this.checkedExecuteUpdateCount(preparedStatement, objects);
		}
	}

	/**
	 * Returns additional SQL "LIMIT" and "OFFSET" clauses.
	 * <p>
	 * (Convenience method for HSQLDB repository subclasses).
	 * 
	 * @param limit
	 * @param offset
	 * @return SQL string, potentially empty but never null
	 */
	public static String limitOffsetSql(Integer limit, Integer offset) {
		String sql = "";

		if (limit != null && limit > 0)
			sql += " LIMIT " + limit;

		if (offset != null)
			sql += " OFFSET " + offset;

		return sql;
	}

	/** Logs other HSQLDB sessions then re-throws passed exception */
	public SQLException examineException(SQLException e) throws SQLException {
		LOGGER.error("SQL error: " + e.getMessage());

		// Serialization failure / potential deadlock - so list other sessions
		try (ResultSet resultSet = this.checkedExecute(
				"SELECT session_id, transaction, transaction_size, waiting_for_this, this_waiting_for, current_statement FROM Information_schema.system_sessions")) {
			if (resultSet == null)
				return e;

			do {
				long sessionId = resultSet.getLong(1);
				boolean inTransaction = resultSet.getBoolean(2);
				long transactionSize = resultSet.getLong(3);
				String waitingForThis = resultSet.getString(4);
				String thisWaitingFor = resultSet.getString(5);
				String currentStatement = resultSet.getString(6);

				LOGGER.error(String.format("Session %d, %s transaction (size %d), waiting for this '%s', this waiting for '%s', current statement: %s",
						sessionId, (inTransaction ? "in" : "not in"), transactionSize, waitingForThis, thisWaitingFor, currentStatement));
			} while (resultSet.next());
		} catch (SQLException de) {
			// Throw original exception instead
			return e;
		}

		return e;
	}

	/** Converts milliseconds from epoch to OffsetDateTime needed for TIMESTAMP WITH TIME ZONE columns. */
	/* package */ static OffsetDateTime toOffsetDateTime(long timestamp) {
		return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
	}

	/** Converts OffsetDateTime from TIMESTAMP WITH TIME ZONE column to milliseconds from epoch. */
	/* package */ static long fromOffsetDateTime(OffsetDateTime offsetDateTime) {
		return offsetDateTime.toInstant().toEpochMilli();
	}

	/** Returns TIMESTAMP WITH TIME ZONE column value as milliseconds from epoch, or null. */
	/* package */ static Long getZonedTimestampMilli(ResultSet resultSet, int columnIndex) throws SQLException {
		OffsetDateTime offsetDateTime = resultSet.getObject(columnIndex, OffsetDateTime.class);
		if (offsetDateTime == null)
			return null;

		return offsetDateTime.toInstant().toEpochMilli();
	}

}