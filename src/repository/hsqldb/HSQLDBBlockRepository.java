package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import data.block.Block;
import data.block.BlockData;
import database.DB;
import qora.account.PublicKeyAccount;
import repository.BlockRepository;
import repository.DataException;

public class HSQLDBBlockRepository implements BlockRepository
{
	protected static final int TRANSACTIONS_SIGNATURE_LENGTH = 64;
	protected static final int GENERATOR_SIGNATURE_LENGTH = 64;
	protected static final int REFERENCE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;

	private static final String BLOCK_DB_COLUMNS = "version, reference, transaction_count, total_fees, "
			+ "transactions_signature, height, generation, generating_balance, generator, generator_signature, AT_data, AT_fees";

	protected HSQLDBRepository repository;
	
	public HSQLDBBlockRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}
	
	public BlockData fromSignature(byte[] signature) throws DataException
	{		
		ResultSet rs;
		try {
			rs = DB.checkedExecute(repository.connection, "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE signature = ?", signature);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
		return getBlockFromResultSet(rs);
	}

	public BlockData fromHeight(int height) throws DataException
	{		
		ResultSet rs;
		try {
			rs = DB.checkedExecute(repository.connection, "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE height = ?", height);
		} catch (SQLException e) {
			throw new DataException("Error loading data from DB", e);
		}
		return getBlockFromResultSet(rs);
	}

	private BlockData getBlockFromResultSet(ResultSet rs) throws DataException {
		try {
			int version = rs.getInt(1);
			byte[] reference = DB.getResultSetBytes(rs.getBinaryStream(2));
			int transactionCount = rs.getInt(3);
			BigDecimal totalFees = rs.getBigDecimal(4);
			byte[] transactionsSignature = DB.getResultSetBytes(rs.getBinaryStream(5));
			int height = rs.getInt(6);
			long timestamp = rs.getTimestamp(7).getTime();
			BigDecimal generatingBalance = rs.getBigDecimal(8);
			byte[] generatorPublicKey = DB.getResultSetBytes(rs.getBinaryStream(9));
			byte[] generatorSignature = DB.getResultSetBytes(rs.getBinaryStream(10));
			byte[] atBytes = DB.getResultSetBytes(rs.getBinaryStream(11));
			BigDecimal atFees = rs.getBigDecimal(12);
	
			return new Block(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp,
					generatingBalance,generatorPublicKey, generatorSignature, atBytes, atFees);
		}
		catch(SQLException e)
		{
			throw new DataException("Error extracting data from result set", e);
		}
	}
}
