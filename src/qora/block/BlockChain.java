package qora.block;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import qora.assets.Asset;

/**
 * Class representing the blockchain as a whole.
 *
 */
public class BlockChain {

	/**
	 * Some sort start-up/initialization/checking method.
	 * 
	 * @throws SQLException
	 */
	public static void validate() throws SQLException {
		// Check first block is Genesis Block
		if (!isGenesisBlockValid())
			rebuildBlockchain();
	}

	private static boolean isGenesisBlockValid() throws SQLException {
		int blockchainHeight = getHeight();
		if (blockchainHeight < 1)
			return false;

		Block block = Block.fromHeight(1);
		if (block == null)
			return false;

		return GenesisBlock.isGenesisBlock(block);
	}

	private static void rebuildBlockchain() throws SQLException {
		// (Re)build database
		DB.rebuild();

		try (final Connection connection = DB.getConnection()) {
			// Add Genesis Block
			GenesisBlock genesisBlock = GenesisBlock.getInstance();
			genesisBlock.process(connection);

			// Add QORA asset.
			// NOTE: Asset's transaction reference is Genesis Block's generator signature which doesn't exist as a transaction!
			Asset qoraAsset = new Asset(Asset.QORA, genesisBlock.getGenerator(), "Qora", "This is the simulated Qora asset.", 10_000_000_000L, true,
					genesisBlock.getGeneratorSignature());
			qoraAsset.save(connection);
		}
	}

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
	public static int getHeight() throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			ResultSet rs = DB.checkedExecute(connection.prepareStatement("SELECT MAX(height) FROM Blocks"));
			if (rs == null)
				return 0;

			return rs.getInt(1);
		}
	}

}
