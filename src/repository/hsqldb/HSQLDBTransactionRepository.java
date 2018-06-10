package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import data.account.PublicKeyAccount;
import data.transaction.Transaction;
import data.transaction.Transaction.TransactionType;
import database.DB;
import qora.block.Block;
import repository.TransactionRepository;

public class HSQLDBTransactionRepository implements TransactionRepository {

	private HSQLDBGenesisTransactionRepository genesisTransactionRepository;

	public HSQLDBTransactionRepository() {
		genesisTransactionRepository = new HSQLDBGenesisTransactionRepository();
	}

	public Transaction fromSignature(byte[] signature) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT type, reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] reference = DB.getResultSetBytes(rs.getBinaryStream(2));
			PublicKeyAccount creator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(3)));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			return null;
		}
	}

	public Transaction fromReference(byte[] reference) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT type, signature, creator, creation, fee FROM Transactions WHERE reference = ?", reference);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] signature = DB.getResultSetBytes(rs.getBinaryStream(2));
			PublicKeyAccount creator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(3)));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			return null;
		}
	}

	private Transaction fromBase(TransactionType type, byte[] signature, byte[] reference, PublicKeyAccount creator, long timestamp, BigDecimal fee) {
		switch (type) {
			case GENESIS:
				return this.genesisTransactionRepository.fromBase(signature, reference, creator, timestamp, fee);

			default:
				return null;
		}
	}

	@Override
	public int getHeight(Transaction transaction) {
		byte[] signature = transaction.getSignature();
		if (signature == null)
			return 0;

		// in one go?
		try {
			ResultSet rs = DB.checkedExecute("SELECT height from BlockTransactions JOIN Blocks ON Blocks.signature = BlockTransactions.block_signature WHERE transaction_signature = ? LIMIT 1", signature);
			if (rs == null)
				return 0;
			
			return rs.getInt(1);
		} catch (SQLException e) {
			return 0;
		}
	}
	
	@Override
	public Block toBlock(Transaction transaction) {
		byte[] signature = transaction.getSignature();
		if (signature == null)
			return null;

		// Fetch block signature (if any)
		try {
			ResultSet rs = DB.checkedExecute("SELECT block_signature from BlockTransactions WHERE transaction_signature = ? LIMIT 1", signature);
			if (rs == null)
				return null;
			
			byte[] blockSignature = DB.getResultSetBytes(rs.getBinaryStream(1));
			
			// TODO
			// return RepositoryManager.getBlockRepository().fromSignature(blockSignature);
			
			return null;
		} catch (SQLException e) {
			return null;
		}
	}

	@Override
	public void save(Transaction transaction) {
		HSQLDBSaver saver = new HSQLDBSaver("Transactions");
		saver.bind("signature", transaction.getSignature()).bind("reference", transaction.getReference()).bind("type", transaction.getType().value)
				.bind("creator", transaction.getCreator().getPublicKey()).bind("creation", new Timestamp(transaction.getTimestamp())).bind("fee", transaction.getFee())
				.bind("milestone_block", null);
		try {
			saver.execute();
		} catch (SQLException e) {
			// XXX do what?
		}
	}

	@Override
	public void delete(Transaction transaction) {
		// NOTE: The corresponding row in sub-table is deleted automatically by the database thanks to "ON DELETE CASCADE" in the sub-table's FOREIGN KEY
		// definition.
		try {
			DB.checkedExecute("DELETE FROM Transactions WHERE signature = ?", transaction.getSignature());
		} catch (SQLException e) {
			// XXX do what?
		}
	}

}
