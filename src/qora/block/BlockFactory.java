package qora.block;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import data.block.BlockData;
import database.DB;
import database.NoDataFoundException;
import qora.account.PublicKeyAccount;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import repository.BlockRepository;
import repository.hsqldb.HSQLDBBlockRepository;

public class BlockFactory {

	// XXX repository should be pushed here from the root entry, no need to know the repository type
	private static BlockRepository repository = new HSQLDBBlockRepository();
	
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
	public static Block fromHeight(int height) {
		if (height == 1)
			return GenesisBlock.getInstance();

		try {
			BlockData data = repository.fromHeight(height);
	
			// TODO fill this list from TransactionFactory
			List<Transaction> transactions = new ArrayList<Transaction>();
			
			// TODO fetch account for data.getGeneratorPublicKey()
			PublicKeyAccount generator = null;
			
			return new Block(data.getVersion(), data.getReference(), data.getTimestamp(), data.getGeneratingBalance(),
					generator,data.getGeneratorSignature(),data.getTransactionsSignature(),
					data.getAtBytes(), data.getAtFees(), transactions);
		} catch (Exception e) { // XXX move NoDataFoundException to repository domain and use it here?
			return null;
		}
	}

	// Navigation

	// Converters

	// Processing

}
