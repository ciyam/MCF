package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.repository.AccountRepository;
import org.qora.repository.DataException;

public class HSQLDBAccountRepository implements AccountRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAccountRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// General account

	@Override
	public AccountData getAccount(String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT reference, public_key, default_group_id FROM Accounts WHERE account = ?", address)) {
			if (resultSet == null)
				return null;

			byte[] reference = resultSet.getBytes(1);
			byte[] publicKey = resultSet.getBytes(2);
			int defaultGroupId = resultSet.getInt(3);

			return new AccountData(address, reference, publicKey, defaultGroupId);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account info from repository", e);
		}
	}

	@Override
	public byte[] getLastReference(String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT reference FROM Accounts WHERE account = ?", address)) {
			if (resultSet == null)
				return null;

			return resultSet.getBytes(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's last reference from repository", e);
		}
	}

	@Override
	public Integer getDefaultGroupId(String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT default_group_id FROM Accounts WHERE account = ?", address)) {
			if (resultSet == null)
				return null;

			// Column is NOT NULL so this should never implicitly convert to 0
			return resultSet.getInt(1); 
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's default groupID from repository", e);
		}
	}

	@Override
	public void ensureAccount(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

		saveHelper.bind("account", accountData.getAddress());

		byte[] publicKey = accountData.getPublicKey();
		if (publicKey != null)
			saveHelper.bind("public_key", publicKey);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to ensure minimal account in repository", e);
		}
	}

	@Override
	public void setLastReference(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

		saveHelper.bind("account", accountData.getAddress()).bind("reference", accountData.getReference());

		byte[] publicKey = accountData.getPublicKey();
		if (publicKey != null)
			saveHelper.bind("public_key", publicKey);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account's last reference into repository", e);
		}
	}

	@Override
	public void setDefaultGroupId(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

		saveHelper.bind("account", accountData.getAddress()).bind("default_group_id", accountData.getDefaultGroupId());

		byte[] publicKey = accountData.getPublicKey();
		if (publicKey != null)
			saveHelper.bind("public_key", publicKey);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account's default group ID into repository", e);
		}
	}

	@Override
	public void delete(String address) throws DataException {
		// NOTE: Account balances are deleted automatically by the database thanks to "ON DELETE CASCADE" in AccountBalances' FOREIGN KEY
		// definition.
		try {
			this.repository.delete("Accounts", "account = ?", address);
		} catch (SQLException e) {
			throw new DataException("Unable to delete account from repository", e);
		}
	}

	// Account balances

	@Override
	public AccountBalanceData getBalance(String address, long assetId) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT balance FROM AccountBalances WHERE account = ? AND asset_id = ?", address, assetId)) {
			if (resultSet == null)
				return null;

			BigDecimal balance = resultSet.getBigDecimal(1).setScale(8);

			return new AccountBalanceData(address, assetId, balance);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account balance from repository", e);
		}
	}

	@Override
	public List<AccountBalanceData> getAllBalances(String address, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT asset_id, balance FROM AccountBalances WHERE account = ? ORDER BY asset_id";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<AccountBalanceData> balances = new ArrayList<AccountBalanceData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
			if (resultSet == null)
				return balances;

			do {
				long assetId = resultSet.getLong(1);
				BigDecimal balance = resultSet.getBigDecimal(2).setScale(8);

				balances.add(new AccountBalanceData(address, assetId, balance));
			} while (resultSet.next());

			return balances;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account balances from repository", e);
		}
	}

	@Override
	public List<AccountBalanceData> getAssetBalances(long assetId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT account, balance FROM AccountBalances WHERE asset_id = ? ORDER BY account";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<AccountBalanceData> balances = new ArrayList<AccountBalanceData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, assetId)) {
			if (resultSet == null)
				return balances;

			do {
				String address = resultSet.getString(1);
				BigDecimal balance = resultSet.getBigDecimal(2).setScale(8);

				balances.add(new AccountBalanceData(address, assetId, balance));
			} while (resultSet.next());

			return balances;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset account balances from repository", e);
		}
	}

	@Override
	public void save(AccountBalanceData accountBalanceData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountBalances");

		saveHelper.bind("account", accountBalanceData.getAddress()).bind("asset_id", accountBalanceData.getAssetId()).bind("balance",
				accountBalanceData.getBalance());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account balance into repository", e);
		}
	}

	@Override
	public void delete(String address, long assetId) throws DataException {
		try {
			this.repository.delete("AccountBalances", "account = ? and asset_id = ?", address, assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete account balance from repository", e);
		}
	}

}
