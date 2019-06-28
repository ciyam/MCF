package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.qora.api.model.BlockForgerSummary;
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
			throw new DataException("Error fetching block by signature from repository", e);
		}
	}

	@Override
	public BlockData fromReference(byte[] reference) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE reference = ?", reference)) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error fetching block by reference from repository", e);
		}
	}

	@Override
	public BlockData fromHeight(int height) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height = ?", height)) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error fetching block by height from repository", e);
		}
	}

	@Override
	public int getHeightFromSignature(byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT height FROM Blocks WHERE signature = ?", signature)) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Error obtaining block height by signature from repository", e);
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
			throw new DataException("Error obtaining block height by timestamp from repository", e);
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
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks ORDER BY height DESC LIMIT 1")) {
			return getBlockFromResultSet(resultSet);
		} catch (SQLException e) {
			throw new DataException("Error fetching last block from repository", e);
		}
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
		String subquerySql = "SELECT proxy_public_key FROM ProxyForgers WHERE forger = ?";

		String sql = "SELECT COUNT(*) FROM Blocks WHERE generator IN (?, (" + subquerySql + ")) LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, publicKey, publicKey)) {
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch forged blocks count from repository", e);
		}
	}

	@Override
	public List<BlockForgerSummary> getBlockForgers(List<String> addresses, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String subquerySql = "SELECT generator, COUNT(signature) FROM Blocks GROUP BY generator ORDER BY COUNT(signature) ";
		if (reverse != null && reverse)
			subquerySql += " DESC";

		String sql = "SELECT DISTINCT generator, n_blocks, forger, recipient FROM (" + subquerySql + ") AS Forgers (generator, n_blocks) "
			+ " LEFT OUTER JOIN ProxyForgers ON proxy_public_key = generator ";

		if (addresses != null && !addresses.isEmpty()) {
			sql += " LEFT OUTER JOIN Accounts AS GeneratorAccounts ON GeneratorAccounts.public_key = generator "
				+ " LEFT OUTER JOIN Accounts AS ForgerAccounts ON ForgerAccounts.public_key = forger "
				+ " JOIN (VALUES " + String.join(", ", Collections.nCopies(addresses.size(), "(?)")) + ") AS FilterAccounts (account) "
				+ " ON FilterAccounts.account IN (recipient, GeneratorAccounts.account, ForgerAccounts.account) ";
		} else {
			addresses = Collections.emptyList();
		}

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<BlockForgerSummary> summaries = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, addresses.toArray())) {
			if (resultSet == null)
				return summaries;

			do {
				byte[] generator = resultSet.getBytes(1);
				int nBlocks = resultSet.getInt(2);
				byte[] forger = resultSet.getBytes(3);
				String recipient = resultSet.getString(4);

				summaries.add(new BlockForgerSummary(generator, nBlocks, forger, recipient));
			} while (resultSet.next());

			return summaries;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch generator's blocks from repository", e);
		}
	}

	@Override
	public List<BlockData> getBlocksWithGenerator(byte[] generatorPublicKey, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE generator = ? ORDER BY height ";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<BlockData> blockData = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, generatorPublicKey)) {
			if (resultSet == null)
				return blockData;

			do {
				blockData.add(getBlockFromResultSet(resultSet));
			} while (resultSet.next());

			return blockData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch generator's blocks from repository", e);
		}
	}

	@Override
	public List<BlockData> getBlocks(int firstBlockHeight, int lastBlockHeight) throws DataException {
		String sql = "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height BETWEEN ? AND ?";

		List<BlockData> blockData = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, firstBlockHeight, lastBlockHeight)) {
			if (resultSet == null)
				return blockData;

			do {
				blockData.add(getBlockFromResultSet(resultSet));
			} while (resultSet.next());

			return blockData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch height-ranged blocks from repository", e);
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
