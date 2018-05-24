package qora.block;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import database.DB;
import database.NoDataFoundException;

public class BlockFactory {

	/**
	 * Load Block from DB using block signature.
	 * 
	 * @param signature
	 * @return ? extends Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromSignature(byte[] signature) throws SQLException {
		Block block = Block.fromSignature(signature);
		if (block == null)
			return null;

		// Can we promote to a GenesisBlock?
		if (GenesisBlock.isGenesisBlock(block))
			return GenesisBlock.getInstance();

		// Standard block
		return block;
	}

	/**
	 * Load Block from DB using block height
	 * 
	 * @param height
	 * @return ? extends Block, or null if not found
	 * @throws SQLException
	 */
	public static Block fromHeight(int height) throws SQLException {
		if (height == 1)
			return GenesisBlock.getInstance();

		try (final Connection connection = DB.getConnection()) {
			PreparedStatement preparedStatement = connection.prepareStatement("SELECT signature FROM Blocks WHERE height = ?");
			preparedStatement.setInt(1, height);

			try {
				return new Block(DB.checkedExecute(preparedStatement));
			} catch (NoDataFoundException e) {
				return null;
			}
		}
	}

	// Navigation

	// Converters

	// Processing

}
