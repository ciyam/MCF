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
	
	private static final int REFERENCE_LENGTH = 64;
	
	public Transaction fromSignature(byte[] signature) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT type, reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
			if (rs == null)
				return null;
	
			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] reference = DB.getResultSetBytes(rs.getBinaryStream(1), REFERENCE_LENGTH);
			// Note: can't use CREATOR_LENGTH in case we encounter Genesis Account's short, 8-byte public key
			PublicKeyAccount creator = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(2)));
			long timestamp = rs.getTimestamp(3).getTime();
			BigDecimal fee = rs.getBigDecimal(4).setScale(8);
			
			switch (type) {
				case GENESIS:
					return new HSQLDBGenesisTransaction().fromSignature(signature, reference, creator, timestamp, fee);
	
				default:
					return null;
			}
		} catch (SQLException e) {
			return null;
		}
	}

}
