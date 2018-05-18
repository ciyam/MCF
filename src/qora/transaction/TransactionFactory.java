package qora.transaction;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import qora.transaction.Transaction.TransactionType;

public class TransactionFactory {

	/**
	 * Load Transaction from DB using signature.
	 * 
	 * @param connection
	 * @param signature
	 * @return ? extends Transaction, or null if not found
	 * @throws SQLException
	 */
	public static Transaction fromSignature(Connection connection, byte[] signature) throws SQLException {
		ResultSet resultSet = DB.executeUsingBytes(connection, "SELECT type, signature FROM Transactions WHERE signature = ?", signature);
		return fromResultSet(connection, resultSet);
	}

	/**
	 * Load Transaction from DB using reference.
	 * 
	 * @param connection
	 * @param reference
	 * @return ? extends Transaction, or null if not found
	 * @throws SQLException
	 */
	public static Transaction fromReference(Connection connection, byte[] reference) throws SQLException {
		ResultSet resultSet = DB.executeUsingBytes(connection, "SELECT type, signature FROM Transactions WHERE reference = ?", reference);
		return fromResultSet(connection, resultSet);
	}

	private static Transaction fromResultSet(Connection connection, ResultSet resultSet) throws SQLException {
		if (resultSet == null)
			return null;

		TransactionType type = TransactionType.valueOf(resultSet.getInt(1));
		if (type == null)
			return null;

		byte[] signature = DB.getResultSetBytes(resultSet.getBinaryStream(2), Transaction.SIGNATURE_LENGTH);

		switch (type) {
			case GENESIS:
				return GenesisTransaction.fromSignature(connection, signature);

			case PAYMENT:
				return PaymentTransaction.fromSignature(connection, signature);

			default:
				return null;
		}
	}

}
