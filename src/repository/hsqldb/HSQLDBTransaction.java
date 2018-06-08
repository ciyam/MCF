package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import data.account.PublicKeyAccount;
import data.transaction.Transaction;
import data.transaction.Transaction.TransactionType;
import repository.TransactionRepository;

public class HSQLDBTransaction implements TransactionRepository {

	private HSQLDBGenesisTransaction genesisTransactionRepository;

	public HSQLDBTransaction() {
		genesisTransactionRepository = new HSQLDBGenesisTransaction();
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

}
