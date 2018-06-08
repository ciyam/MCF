package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.account.Account;
import data.account.PublicKeyAccount;
import data.transaction.GenesisTransaction;
import data.transaction.Transaction;
import database.DB;

public class HSQLDBGenesisTransaction extends HSQLDBTransaction {

	protected Transaction fromBase(byte[] signature, byte[] reference, PublicKeyAccount creator, long timestamp, BigDecimal fee) {
		try {
			ResultSet rs = DB.checkedExecute("SELECT recipient, amount FROM GenesisTransactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			Account recipient = new Account(rs.getString(1));
			BigDecimal amount = rs.getBigDecimal(2).setScale(8);

			return new GenesisTransaction(recipient, amount, timestamp, signature);
		} catch (SQLException e) {
			return null;
		}
	}

}
