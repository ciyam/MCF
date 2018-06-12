package repository.hsqldb;

import java.sql.Connection;
import java.sql.SQLException;

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

		connectionPool = new JDBCPool();
		connectionPool.setUrl(this.connectionUrl);

		// Perform DB updates?
		try (final Connection connection = connectionPool.getConnection()) {
			HSQLDBDatabaseUpdates.updateDatabase(connection);
		} catch (SQLException e) {
			throw new DataException("Repository initialization error", e);
		}
	}

	public Repository getRepository() throws DataException {
		try {
			return new HSQLDBRepository(this.getConnection());
		} catch (SQLException e) {
			throw new DataException("Repository initialization error", e);
		}
	}

	private Connection getConnection() throws SQLException {
		Connection connection = this.connectionPool.getConnection();

		// start transaction
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		connection.setAutoCommit(false);

		return connection;
	}

}
