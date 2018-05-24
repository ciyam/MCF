package qora.block;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;

/**
 * Class representing the blockchain as a whole.
 *
 */
public class BlockChain {

	/**
	 * Return block height from DB using signature.
	 * 
	 * @param signature
	 * @return height, or 0 if block not found.
	 * @throws SQLException
	 */
	public static int getBlockHeightFromSignature(byte[] signature) throws SQLException {
		ResultSet rs = DB.executeUsingBytes("SELECT height FROM Blocks WHERE signature = ?", signature);
		if (rs == null)
			return 0;

		return rs.getInt(1);
	}

	/**
	 * Return highest block height from DB.
	 * 
	 * @return height, or 0 if there are no blocks in DB (not very likely).
	 * @throws SQLException
	 */
	public static int getMaxHeight() throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			ResultSet rs = DB.checkedExecute(connection.prepareStatement("SELECT MAX(height) FROM Blocks"));
			if (rs == null)
				return 0;

			return rs.getInt(1);
		}
	}

}
