package org.hsqldb.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import org.hsqldb.jdbc.JDBCPool;
import org.hsqldb.jdbc.pool.JDBCPooledConnection;

public class HSQLDBPool extends JDBCPool {

	public HSQLDBPool(int poolSize) {
		super(poolSize);
	}

	/**
	 * Tries to retrieve a new connection using the properties that have already been
	 * set.
	 *
	 * @return  a connection to the data source, or null if no spare connections in pool
	 * @exception SQLException if a database access error occurs
	 */
	public Connection tryConnection() throws SQLException {
		for (int i = 0; i < states.length(); i++) {
			if (states.compareAndSet(i, RefState.available, RefState.allocated)) {
				return connections[i].getConnection();
			}

			if (states.compareAndSet(i, RefState.empty, RefState.allocated)) {
				try {
					JDBCPooledConnection connection = (JDBCPooledConnection) source.getPooledConnection();

					connection.addConnectionEventListener(this);
					connection.addStatementEventListener(this);
					connections[i] = connection;

					return connections[i].getConnection();
				} catch (SQLException e) {
					states.set(i, RefState.empty);
				}
			}
		}

		return null;
	}

}
