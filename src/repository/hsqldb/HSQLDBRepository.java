package repository.hsqldb;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.sql.Connection;
import java.sql.SQLException;

import database.DB;
import repository.DataException;
import repository.Repository;

public class HSQLDBRepository extends Repository {

	Connection connection;
	
	public HSQLDBRepository()  throws DataException {
		try {
			initialize();
		} catch (SQLException e) {
			throw new DataException("initialization error", e);
		}
		
		this.transactionRepository = new HSQLDBTransactionRepository(this);
	}

	private void initialize() throws SQLException {
		connection = DB.getPoolConnection();
		
		// start transaction
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		connection.setAutoCommit(false);
	}

	@Override
	public void saveChanges() throws DataException {
		try {
			connection.commit();
		} catch (SQLException e) {
			throw new DataException("commit error", e);
		}		
	}

	@Override
	public void discardChanges() throws DataException {
		try {
			connection.rollback();
		} catch (SQLException e) {
			throw new DataException("rollback error", e);
		}		
	}

	// TODO prevent leaking of connections if .close() is not called before garbage collection of the repository.
	// Maybe use PhantomReference to call .close() on connection after repository destruction?
	@Override
	public void close() throws DataException {
		try {
			// give connection back to the pool
			connection.close();
			connection = null;
		} catch (SQLException e) {
			throw new DataException("close error", e);
		}		
	}

}
