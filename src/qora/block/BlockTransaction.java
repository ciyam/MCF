package qora.block;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import database.DB;
import database.NoDataFoundException;
import database.SaveHelper;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;

public class BlockTransaction {

	// Database properties shared with all transaction types
	protected byte[] blockSignature;
	protected int sequence;
	protected byte[] transactionSignature;

	// Constructors

	public BlockTransaction(byte[] blockSignature, int sequence, byte[] transactionSignature) {
		this.blockSignature = blockSignature;
		this.sequence = sequence;
		this.transactionSignature = transactionSignature;
	}

	// Getters/setters

	public byte[] getBlockSignature() {
		return this.blockSignature;
	}

	public int getSequence() {
		return this.sequence;
	}

	public byte[] getTransactionSignature() {
		return this.transactionSignature;
	}

	// More information

	// Load/Save

	protected BlockTransaction(byte[] blockSignature, int sequence) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			ResultSet rs = DB.checkedExecute("SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ? and sequence = ?", blockSignature,
					sequence);
			if (rs == null)
				throw new NoDataFoundException();

			this.blockSignature = blockSignature;
			this.sequence = sequence;
			this.transactionSignature = DB.getResultSetBytes(rs.getBinaryStream(1), Transaction.SIGNATURE_LENGTH);
		}
	}

	protected BlockTransaction(byte[] transactionSignature) throws SQLException {
		ResultSet rs = DB.checkedExecute("SELECT block_signature, sequence FROM BlockTransactions WHERE transaction_signature = ?", transactionSignature);
		if (rs == null)
			throw new NoDataFoundException();

		this.blockSignature = DB.getResultSetBytes(rs.getBinaryStream(1), Block.BLOCK_SIGNATURE_LENGTH);
		this.sequence = rs.getInt(2);
		this.transactionSignature = transactionSignature;
	}

	/**
	 * Load BlockTransaction from DB using block signature and tx-in-block sequence.
	 * 
	 * @param blockSignature
	 * @param sequence
	 * @return BlockTransaction, or null if not found
	 * @throws SQLException
	 */
	public static BlockTransaction fromBlockSignature(byte[] blockSignature, int sequence) throws SQLException {
		try {
			return new BlockTransaction(blockSignature, sequence);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	/**
	 * Load BlockTransaction from DB using transaction signature.
	 * 
	 * @param transactionSignature
	 * @return BlockTransaction, or null if not found
	 * @throws SQLException
	 */
	public static BlockTransaction fromTransactionSignature(byte[] transactionSignature) throws SQLException {
		try {
			return new BlockTransaction(transactionSignature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	protected void save(Connection connection) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "BlockTransactions");
		saveHelper.bind("block_signature", this.blockSignature).bind("sequence", this.sequence).bind("transaction_signature", this.transactionSignature);
		saveHelper.execute();
	}

	// Navigation

	/**
	 * Load corresponding Block from DB.
	 * 
	 * @return Block, or null if not found (which should never happen)
	 * @throws SQLException
	 */
	public Block getBlock() throws SQLException {
		return Block.fromSignature(this.blockSignature);
	}

	/**
	 * Load corresponding Transaction from DB.
	 * 
	 * @return Transaction, or null if not found (which should never happen)
	 * @throws SQLException
	 */
	public Transaction getTransaction() throws SQLException {
		return TransactionFactory.fromSignature(this.transactionSignature);
	}

	// Converters

	public JSONObject toJSON() {
		// TODO
		return null;
	}

	// Processing

}
