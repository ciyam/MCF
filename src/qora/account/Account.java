package qora.account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import database.SaveHelper;

public class Account {

	public static final int ADDRESS_LENGTH = 25;

	protected String address;

	protected Account() {
	}

	public Account(String address) {
		this.address = address;
	}

	public String getAddress() {
		return this.address;
	}

	@Override
	public boolean equals(Object b) {
		if (!(b instanceof Account))
			return false;

		return this.getAddress().equals(((Account) b).getAddress());
	}

	// Balance manipulations - assetId is 0 for QORA

	public BigDecimal getBalance(long assetId, int confirmations) {
		// TODO
		return null;
	}

	public BigDecimal getConfirmedBalance(long assetId) throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			return getConfirmedBalance(connection, assetId);
		}
	}

	public BigDecimal getConfirmedBalance(Connection connection, long assetId) throws SQLException {
		ResultSet resultSet = DB.checkedExecute(connection, "SELECT balance FROM AccountBalances WHERE account = ? and asset_id = ?", this.getAddress(),
				assetId);
		if (resultSet == null)
			return BigDecimal.ZERO.setScale(8);

		return resultSet.getBigDecimal(1);
	}

	public void setConfirmedBalance(Connection connection, long assetId, BigDecimal balance) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "AccountBalances");
		saveHelper.bind("account", this.getAddress()).bind("asset_id", assetId).bind("balance", balance);
		saveHelper.execute();
	}

	public void deleteBalance(Connection connection, long assetId) throws SQLException {
		DB.checkedExecute(connection, "DELETE FROM AccountBalances WHERE account = ? and asset_id = ?", this.getAddress(), assetId);
	}

	// Reference manipulations

	/**
	 * Fetch last reference for account.
	 * 
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws SQLException
	 */
	public byte[] getLastReference() throws SQLException {
		try (final Connection connection = DB.getConnection()) {
			return getLastReference(connection);
		}
	}

	/**
	 * Fetch last reference for account using supplied DB connection.
	 * <p>
	 * Typically for use within an ongoing SQL Transaction.
	 * 
	 * @param connection
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws SQLException
	 */
	public byte[] getLastReference(Connection connection) throws SQLException {
		ResultSet resultSet = DB.checkedExecute(connection, "SELECT reference FROM Accounts WHERE account = ?", this.getAddress());
		if (resultSet == null)
			return null;

		return DB.getResultSetBytes(resultSet.getBinaryStream(1));
	}

	/**
	 * Set last reference for account.
	 * 
	 * @param connection
	 * @param reference
	 *            -- null allowed
	 * @throws SQLException
	 */
	public void setLastReference(Connection connection, byte[] reference) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "Accounts");
		saveHelper.bind("account", this.getAddress()).bind("reference", reference);
		saveHelper.execute();
	}

}
