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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.repository.ATRepository;
import org.qora.repository.AccountRepository;
import org.qora.repository.ArbitraryRepository;
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
import org.qora.settings.Settings;

public class HSQLDBRepository implements Repository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBRepository.class);

	public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	protected Connection connection;
	protected Deque<Savepoint> savepoints;
	protected boolean debugState = false;
	protected Long slowQueryThreshold = null;
	protected List<String> sqlStatements;
	protected long sessionId;

	// NB: no visibility modifier so only callable from within same package
	HSQLDBRepository(Connection connection) throws DataException {
		this.connection = connection;
		this.savepoints = new ArrayDeque<>(3);

		this.slowQueryThreshold = Settings.getInstance().getSlowQueryThreshold();
		if (this.slowQueryThreshold != null)
			this.sqlStatements = new ArrayList<String>();

		// Find out our session ID
		try (Statement stmt = this.connection.createStatement()) {
			if (!stmt.execute("SELECT SESSION_ID()"))
				throw new DataException("Unable to fetch session ID from repository");

			try (ResultSet resultSet = stmt.getResultSet()) {
				if (resultSet == null || !resultSet.next())
					LOGGER.warn("Unable to fetch session ID from repository");

				this.sessionId = resultSet.getLong(1);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to fetch session ID from repository", e);
		}

		assertEmptyTransaction("connection creation");
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
	public ArbitraryRepository getArbitraryRepository() {
		return new HSQLDBArbitraryRepository(this);
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

			// Before clearing statements so we can log what led to assertion error
			assertEmptyTransaction("transaction commit");

			if (this.sqlStatements != null)
				this.sqlStatements.clear();
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

			// Before clearing statements so we can log what led to assertion error
			assertEmptyTransaction("transaction commit");

			if (this.sqlStatements != null)
				this.sqlStatements.clear();
		}
	}

	@Override
	public void setSavepoint() throws DataException {
		try {
			if (this.sqlStatements != null)
				// We don't know savepoint's ID yet
				this.sqlStatements.add("SAVEPOINT [?]");

			Savepoint savepoint = this.connection.setSavepoint();
			this.savepoints.push(savepoint);

			// Update query log with savepoint ID
			if (this.sqlStatements != null)
				this.sqlStatements.set(this.sqlStatements.size() - 1, "SAVEPOINT [" + savepoint.getSavepointId() + "]");
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
			if (this.sqlStatements != null)
				this.sqlStatements.add("ROLLBACK TO SAVEPOINT [" + savepoint.getSavepointId() + "]");

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
			assertEmptyTransaction("connection close");

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
	public boolean getDebug() {
		return this.debugState;
	}

	@Override
	public void setDebug(boolean debugState) {
		this.debugState = debugState;
	}

	/**
	 * Returns prepared statement using passed SQL, logging query if necessary.
	 */
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		if (this.debugState)
			LOGGER.debug(String.format("[%d] %s", this.sessionId, sql));

		if (this.sqlStatements != null)
			this.sqlStatements.add(sql);

		PreparedStatement preparedStatement = this.connection.prepareStatement(sql);

		return preparedStatement;
	}

	/**
	 * Logs this transaction's SQL statements, if enabled.
	 */
	public void logStatements() {
		if (this.sqlStatements == null)
			return;

		LOGGER.info(String.format("HSQLDB SQL statements (session %d) leading up to this were:", this.sessionId));

		for (String sql : this.sqlStatements)
			LOGGER.info(sql);
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
		PreparedStatement preparedStatement = this.prepareStatement(sql);

		// Close the PreparedStatement when the ResultSet is closed otherwise there's a potential resource leak.
		// We can't use try-with-resources here as closing the PreparedStatement on return would also prematurely close the ResultSet.
		preparedStatement.closeOnCompletion();

		long beforeQuery = System.currentTimeMillis();

		ResultSet resultSet = this.checkedExecuteResultSet(preparedStatement, objects);

		long queryTime = System.currentTimeMillis() - beforeQuery;
		if (this.slowQueryThreshold != null && queryTime > this.slowQueryThreshold) {
			LOGGER.info(String.format("HSQLDB query took %d ms: %s", queryTime, sql));

			logStatements();
		}

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
		// We don't need to use HSQLDBRepository.prepareStatement for this as it's so trivial
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
		String sql = "SELECT TRUE FROM " + tableName + " WHERE " + whereClause + " LIMIT 1";

		try (PreparedStatement preparedStatement = this.prepareStatement(sql);
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
		String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;

		try (PreparedStatement preparedStatement = this.prepareStatement(sql)) {
			return this.checkedExecuteUpdateCount(preparedStatement, objects);
		}
	}

	/**
	 * Delete all rows from database table.
	 * 
	 * @param tableName
	 * @throws SQLException
	 */
	public int delete(String tableName) throws SQLException {
		String sql = "DELETE FROM " + tableName;

		try (PreparedStatement preparedStatement = this.prepareStatement(sql)) {
			return this.checkedExecuteUpdateCount(preparedStatement);
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
		LOGGER.error(String.format("HSQLDB error (session %d): %s", this.sessionId, e.getMessage()), e);

		logStatements();

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

	private void assertEmptyTransaction(String context) throws DataException {
		try (Statement stmt = this.connection.createStatement()) {
			// Diagnostic check for uncommitted changes
			if (!stmt.execute("SELECT transaction, transaction_size FROM information_schema.system_sessions WHERE session_id = " + this.sessionId)) // TRANSACTION_SIZE() broken?
				throw new DataException("Unable to check repository status after " + context);

			try (ResultSet resultSet = stmt.getResultSet()) {
				if (resultSet == null || !resultSet.next())
					LOGGER.warn("Unable to check repository status after " + context);

				boolean inTransaction = resultSet.getBoolean(1);
				int transactionCount = resultSet.getInt(2);

				if (inTransaction && transactionCount != 0) {
					LOGGER.warn(String.format("Uncommitted changes (%d) after %s, session [%d]", transactionCount, context, this.sessionId), new Exception("Uncommitted repository changes"));
					logStatements();
				}
			}
		} catch (SQLException e) {
			throw new DataException("Error checking repository status after " + context, e);
		}
	}

	/** Converts milliseconds from epoch to OffsetDateTime needed for TIMESTAMP WITH TIME ZONE columns. */
	/* package */ static OffsetDateTime toOffsetDateTime(Long timestamp) {
		if (timestamp == null)
			return null;

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

	/** Convenience method to return n comma-separated, placeholders as a string. */
	public static String nPlaceholders(int n) {
		return String.join(", ", Collections.nCopies(n, "?"));
	}

	/** Convenience method to return n comma-separated, bracketed, placeholders as a string. */
	public static String nValuesPlaceholders(int n) {
		return String.join(", ", Collections.nCopies(n, "(?)"));
	}

}