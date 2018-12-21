package repository.hsqldb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.hsqldb.jdbc.JDBCPool;

import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;

public class HSQLDBRepositoryFactory implements RepositoryFactory {

	private String connectionUrl;
	private JDBCPool connectionPool;

	public HSQLDBRepositoryFactory(String connectionUrl) throws DataException {
		// one-time initialization goes in here
		this.connectionUrl = connectionUrl;

		// Check no-one else is accessing database
		try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
		} catch (SQLException e) {
			throw new DataException("Unable to open repository: " + e.getMessage());
		}

		this.connectionPool = new JDBCPool();
		this.connectionPool.setUrl(this.connectionUrl);

		Properties properties = new Properties();
		properties.setProperty("close_result", "true"); // Auto-close old ResultSet if Statement creates new ResultSet
		this.connectionPool.setProperties(properties);

		// Perform DB updates?
		try (final Connection connection = this.connectionPool.getConnection()) {
			HSQLDBDatabaseUpdates.updateDatabase(connection);
		} catch (SQLException e) {
			throw new DataException("Repository initialization error", e);
		}
	}

	@Override
	public Repository getRepository() throws DataException {
		try {
			return new HSQLDBRepository(this.getConnection());
		} catch (SQLException e) {
			throw new DataException("Repository initialization error", e);
		}
	}

	private Connection getConnection() throws SQLException {
		Connection connection = this.connectionPool.getConnection();

		// Set transaction level
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		connection.setAutoCommit(false);

		return connection;
	}

	@Override
	public void close() throws DataException {
		try {
			// Close all existing connections immediately
			this.connectionPool.close(0);

			// Now that all connections are closed, create a dedicated connection to shut down repository
			try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
				connection.createStatement().execute("SHUTDOWN");
			}
		} catch (SQLException e) {
			throw new DataException("Error during repository shutdown", e);
		}
	}

}
