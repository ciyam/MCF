package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import data.block.BlockData;
import data.block.BlockTransactionData;
import data.transaction.TransactionData;
import repository.BlockRepository;
import repository.DataException;
import repository.TransactionRepository;

public class HSQLDBBlockRepository implements BlockRepository {

	private static final String BLOCK_DB_COLUMNS = "version, reference, transaction_count, total_fees, "
			+ "transactions_signature, height, generation, generating_balance, generator, generator_signature, AT_data, AT_fees";

	protected HSQLDBRepository repository;

	public HSQLDBBlockRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	private BlockData getBlockFromResultSet(ResultSet rs) throws DataException {
		if (rs == null)
			return null;

		try {
			int version = rs.getInt(1);
			byte[] reference = rs.getBytes(2);
			int transactionCount = rs.getInt(3);
			BigDecimal totalFees = rs.getBigDecimal(4);
			byte[] transactionsSignature = rs.getBytes(5);
			int height = rs.getInt(6);
			long timestamp = rs.getTimestamp(7).getTime();
			BigDecimal generatingBalance = rs.getBigDecimal(8);
			byte[] generatorPublicKey = rs.getBytes(9);
			byte[] generatorSignature = rs.getBytes(10);
			byte[] atBytes = rs.getBytes(11);
			BigDecimal atFees = rs.getBigDecimal(12);

			return new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
					generatorPublicKey, generatorSignature, atBytes, atFees);
		} catch (SQLException e) {
			throw new DataException("Error extracting data from result set", e);
		}
	}

	public BlockData fromSignature(byte[] signature) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE signature = ?", signature);
			return getBlockFromResultSet(rs);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	public BlockData fromReference(byte[] reference) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height = ?", reference);
			return getBlockFromResultSet(rs);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	public BlockData fromHeight(int height) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height = ?", height);
			return getBlockFromResultSet(rs);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	public int getHeightFromSignature(byte[] signature) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT height FROM Blocks WHERE signature = ?", signature);
			if (rs == null)
				return 0;

			return rs.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining block height from repository", e);
		}
	}

	public int getBlockchainHeight() throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT MAX(height) FROM Blocks");
			if (rs == null)
				return 0;

			return rs.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining blockchain height from repository", e);
		}
	}

	public BlockData getLastBlock() throws DataException {
		return fromHeight(getBlockchainHeight());
	}

	public List<TransactionData> getTransactionsFromSignature(byte[] signature) throws DataException {
		List<TransactionData> transactions = new ArrayList<TransactionData>();

		try {
			ResultSet rs = this.repository.checkedExecute("SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ?", signature);
			if (rs == null)
				return transactions; // No transactions in this block

			TransactionRepository transactionRepo = this.repository.getTransactionRepository();

			// NB: do-while loop because .checkedExecute() implicitly calls ResultSet.next() for us
			do {
				byte[] transactionSignature = rs.getBytes(1);
				transactions.add(transactionRepo.fromSignature(transactionSignature));
			} while (rs.next());
		} catch (SQLException e) {
			throw new DataException(e);
		}

		return transactions;
	}

	public void save(BlockData blockData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Blocks");

		saveHelper.bind("signature", blockData.getSignature()).bind("version", blockData.getVersion()).bind("reference", blockData.getReference())
				.bind("transaction_count", blockData.getTransactionCount()).bind("total_fees", blockData.getTotalFees())
				.bind("transactions_signature", blockData.getTransactionsSignature()).bind("height", blockData.getHeight())
				.bind("generation", new Timestamp(blockData.getTimestamp())).bind("generating_balance", blockData.getGeneratingBalance())
				.bind("generator", blockData.getGeneratorPublicKey()).bind("generator_signature", blockData.getGeneratorSignature())
				.bind("AT_data", blockData.getAtBytes()).bind("AT_fees", blockData.getAtFees());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save Block into repository", e);
		}
	}

	public void delete(BlockData blockData) throws DataException {
		try {
			this.repository.delete("Blocks", "signature = ?", blockData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete Block from repository", e);
		}
	}

	public void save(BlockTransactionData blockTransactionData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("BlockTransactions");
		saveHelper.bind("block_signature", blockTransactionData.getBlockSignature()).bind("sequence", blockTransactionData.getSequence())
				.bind("transaction_signature", blockTransactionData.getTransactionSignature());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save BlockTransaction into repository", e);
		}
	}

	public void delete(BlockTransactionData blockTransactionData) throws DataException {
		try {
			this.repository.delete("BlockTransactions", "block_signature = ? AND sequence = ? AND transaction_signature = ?",
					blockTransactionData.getBlockSignature(), blockTransactionData.getSequence(), blockTransactionData.getTransactionSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete BlockTransaction from repository", e);
		}
	}

}
