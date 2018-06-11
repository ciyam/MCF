package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.transaction.Transaction.TransactionType;
import database.DB;
import repository.DataException;
import repository.RepositoryManager;
import repository.TransactionRepository;

public class HSQLDBTransactionRepository implements TransactionRepository {

	private HSQLDBGenesisTransactionRepository genesisTransactionRepository;

	public HSQLDBTransactionRepository() {
		genesisTransactionRepository = new HSQLDBGenesisTransactionRepository();
	}

	public TransactionData fromSignature(byte[] signature) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT type, reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] reference = DB.getResultSetBytes(rs.getBinaryStream(2));
			byte[] creator = DB.getResultSetBytes(rs.getBinaryStream(3));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			return null;
		}
	}

	public TransactionData fromReference(byte[] reference) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT type, signature, creator, creation, fee FROM Transactions WHERE reference = ?", reference);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] signature = DB.getResultSetBytes(rs.getBinaryStream(2));
			byte[] creator = DB.getResultSetBytes(rs.getBinaryStream(3));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			return null;
		}
	}

	private TransactionData fromBase(TransactionType type, byte[] signature, byte[] reference, byte[] creator, long timestamp, BigDecimal fee) {
		switch (type) {
			case GENESIS:
				return this.genesisTransactionRepository.fromBase(signature, reference, creator, timestamp, fee);

			default:
				return null;
		}
	}

	@Override
	public int getHeight(TransactionData transactionData) {
		byte[] signature = transactionData.getSignature();
		if (signature == null)
			return 0;

		// in one go?
		try {
			ResultSet rs = DB.checkedExecute(
					"SELECT height from BlockTransactions JOIN Blocks ON Blocks.signature = BlockTransactions.block_signature WHERE transaction_signature = ? LIMIT 1",
					signature);
			if (rs == null)
				return 0;

			return rs.getInt(1);
		} catch (SQLException e) {
			return 0;
		}
	}

	@Override
	public BlockData toBlock(TransactionData transactionData) {
		byte[] signature = transactionData.getSignature();
		if (signature == null)
			return null;

		// Fetch block signature (if any)
		try {
			ResultSet rs = DB.checkedExecute("SELECT block_signature from BlockTransactions WHERE transaction_signature = ? LIMIT 1", signature);
			if (rs == null)
				return null;

			byte[] blockSignature = DB.getResultSetBytes(rs.getBinaryStream(1));

			return RepositoryManager.getBlockRepository().fromSignature(blockSignature);
		} catch (SQLException | DataException e) {
			return null;
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		HSQLDBSaver saver = new HSQLDBSaver("Transactions");
		saver.bind("signature", transactionData.getSignature()).bind("reference", transactionData.getReference()).bind("type", transactionData.getType().value)
				.bind("creator", transactionData.getCreatorPublicKey()).bind("creation", new Timestamp(transactionData.getTimestamp())).bind("fee", transactionData.getFee())
				.bind("milestone_block", null);
		try {
			saver.execute();
		} catch (SQLException e) {
			throw new DataException(e);
		}
	}

	@Override
	public void delete(TransactionData transactionData) {
		// NOTE: The corresponding row in sub-table is deleted automatically by the database thanks to "ON DELETE CASCADE" in the sub-table's FOREIGN KEY
		// definition.
		try {
			DB.checkedExecute("DELETE FROM Transactions WHERE signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			// XXX do what?
		}
	}

}
