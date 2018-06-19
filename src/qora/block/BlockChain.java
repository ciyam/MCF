package qora.block;

import java.math.BigDecimal;
import java.sql.SQLException;

import data.assets.AssetData;
import data.block.BlockData;
import qora.assets.Asset;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

/**
 * Class representing the blockchain as a whole.
 *
 */
public class BlockChain {

	/**
	 * Minimum Qora balance.
	 */
	public static final BigDecimal MIN_BALANCE = BigDecimal.valueOf(1L).setScale(8);
	/**
	 * Maximum Qora balance.
	 */
	public static final BigDecimal MAX_BALANCE = BigDecimal.valueOf(10_000_000_000L).setScale(8);
	/**
	 * Number of blocks between recalculating block's generating balance.
	 */
	public static final int BLOCK_RETARGET_INTERVAL = 10;
	/**
	 * Minimum target time between blocks, in seconds.
	 */
	public static final long MIN_BLOCK_TIME = 60;
	/**
	 * Maximum target time between blocks, in seconds.
	 */
	public static final long MAX_BLOCK_TIME = 300;
	/**
	 * Maximum acceptable timestamp disagreement offset in milliseconds.
	 */
	public static final long BLOCK_TIMESTAMP_MARGIN = 500L;

	// Various release timestamps / block heights
	public static final int MESSAGE_RELEASE_HEIGHT = 99000;
	public static final int AT_BLOCK_HEIGHT_RELEASE = 99000;
	public static final long POWFIX_RELEASE_TIMESTAMP = 1456426800000L; // Block Version 3 // 2016-02-25T19:00:00+00:00
	public static final long ASSETS_RELEASE_TIMESTAMP = 0L; // From Qora epoch
	public static final long VOTING_RELEASE_TIMESTAMP = 1403715600000L; // 2014-06-25T17:00:00+00:00

	/**
	 * Some sort start-up/initialization/checking method.
	 * 
	 * @throws SQLException
	 */
	public static void validate() throws DataException {
		// Check first block is Genesis Block
		if (!isGenesisBlockValid())
			rebuildBlockchain();
	}

	private static boolean isGenesisBlockValid() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockRepository blockRepository = repository.getBlockRepository();

			int blockchainHeight = blockRepository.getBlockchainHeight();
			if (blockchainHeight < 1)
				return false;

			BlockData blockData = blockRepository.fromHeight(1);
			if (blockData == null)
				return false;

			return GenesisBlock.isGenesisBlock(blockData);
		}
	}

	private static void rebuildBlockchain() throws DataException {
		// (Re)build repository
		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.rebuild();

			// Add Genesis Block
			GenesisBlock genesisBlock = new GenesisBlock(repository);
			genesisBlock.process();

			// Add QORA asset.
			// NOTE: Asset's transaction reference is Genesis Block's generator signature which doesn't exist as a transaction!
			AssetData qoraAssetData = new AssetData(Asset.QORA, genesisBlock.getGenerator().getAddress(), "Qora", "This is the simulated Qora asset.",
					10_000_000_000L, true, genesisBlock.getBlockData().getGeneratorSignature());
			repository.getAssetRepository().save(qoraAssetData);

			repository.saveChanges();
		}
	}

	/**
	 * Return Qora balance adjusted to within min/max limits.
	 */
	public static BigDecimal minMaxBalance(BigDecimal balance) {
		if (balance.compareTo(MIN_BALANCE) < 0)
			return MIN_BALANCE;

		if (balance.compareTo(MAX_BALANCE) > 0)
			return MAX_BALANCE;

		return balance;
	}

}
