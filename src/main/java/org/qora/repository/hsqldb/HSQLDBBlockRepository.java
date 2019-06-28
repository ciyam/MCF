package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.qora.data.block.BlockData;
import org.qora.data.block.BlockTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.TransactionRepository;

import static org.qora.repository.hsqldb.HSQLDBRepository.toOffsetDateTime;
import static org.qora.repository.hsqldb.HSQLDBRepository.getZonedTimestampMilli;

public class HSQLDBBlockRepository implements BlockRepository {

	private static final String BLOCK_DB_COLUMNS = "version, reference, transaction_count, total_fees, "
			+ "transactions_signature, height, generation, generating_balance, generator, generator_signature, AT_count, AT_fees";

	protected HSQLDBRepository repository;

	public HSQLDBBlockRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	private BlockData getBlockFromResultSet(ResultSet resultSet) throws DataException {
		if (resultSet == null)
			return null;

		try {
			int version = resultSet.getInt(1);
			byte[] reference = resultSet.getBytes(2);
			int transactionCount = resultSet.getInt(3);
			BigDecimal totalFees = resultSet.getBigDecimal(4);
			byte[] transactionsSignature = resultSet.getBytes(5);
			int height = resultSet.getInt(6);
			long timestamp = getZonedTimestampMilli(resultSet, 7);
			BigDecimal generatingBalance = resultSet.getBigDecimal(8);
			byte[] generatorPublicKey = resultSet.getBytes(9);
			byte[] generatorSignature = resultSet.getBytes(10);
			int atCount = resultSet.getInt(11);
			BigDecimal atFees = resultSet.getBigDecimal(12);

			return new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
					generatorPublicKey, generatorSignature, atCount, atFees);
		} catch (SQLException e) {
			throw new DataException("Error extracting data from result set", e);
		}
	}

	@Override
	public BlockData fromSignature(byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE signature = ?", signature)) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	@Override
	public BlockData fromReference(byte[] reference) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE reference = ?", reference)) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	@Override
	public BlockData fromHeight(int height) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height = ?", height)) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
	}

	@Override
	public int getHeightFromSignature(byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT height FROM Blocks WHERE signature = ?", signature)) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining block height from repository", e);
		}
	}

	@Override
	public int getHeightFromTimestamp(long timestamp) throws DataException {
		// Uses (generation, height) index
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT height FROM Blocks WHERE generation <= ? ORDER BY generation DESC LIMIT 1",
				toOffsetDateTime(timestamp))) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining block height from repository", e);
		}
	}

	@Override
	public int getBlockchainHeight() throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT height FROM Blocks ORDER BY height DESC LIMIT 1")) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining blockchain height from repository", e);
		}
	}

	@Override
	public BlockData getLastBlock() throws DataException {
		return fromHeight(getBlockchainHeight());
	}

	@Override
	public List<TransactionData> getTransactionsFromSignature(byte[] signature, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ? ORDER BY sequence";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, signature)) {
			if (resultSet == null)
				return transactions; // No transactions in this block

			TransactionRepository transactionRepo = this.repository.getTransactionRepository();

			// NB: do-while loop because .checkedExecute() implicitly calls ResultSet.next() for us
			do {
				byte[] transactionSignature = resultSet.getBytes(1);
				transactions.add(transactionRepo.fromSignature(transactionSignature));
			} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch block's transactions from repository", e);
		}

		return transactions;
	}

	@Override
	public int countForgedBlocks(byte[] publicKey) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT COUNT(*) FROM Blocks WHERE generator = ? LIMIT 1", publicKey)) {
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch forged blocks count from repository", e);
		}
	}

	@Override
	public void save(BlockData blockData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Blocks");

		saveHelper.bind("signature", blockData.getSignature()).bind("version", blockData.getVersion()).bind("reference", blockData.getReference())
				.bind("transaction_count", blockData.getTransactionCount()).bind("total_fees", blockData.getTotalFees())
				.bind("transactions_signature", blockData.getTransactionsSignature()).bind("height", blockData.getHeight())
				.bind("generation", toOffsetDateTime(blockData.getTimestamp())).bind("generating_balance", blockData.getGeneratingBalance())
				.bind("generator", blockData.getGeneratorPublicKey()).bind("generator_signature", blockData.getGeneratorSignature())
				.bind("AT_count", blockData.getATCount()).bind("AT_fees", blockData.getATFees());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save Block into repository", e);
		}
	}

	@Override
	public void delete(BlockData blockData) throws DataException {
		try {
			this.repository.delete("Blocks", "signature = ?", blockData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete Block from repository", e);
		}
	}

	@Override
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

	@Override
	public void delete(BlockTransactionData blockTransactionData) throws DataException {
		try {
			this.repository.delete("BlockTransactions", "block_signature = ? AND sequence = ? AND transaction_signature = ?",
					blockTransactionData.getBlockSignature(), blockTransactionData.getSequence(), blockTransactionData.getTransactionSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete BlockTransaction from repository", e);
		}
	}

}
