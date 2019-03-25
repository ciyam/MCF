package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
	public List<AccountBalanceData> getAssetBalances(List<String> addresses, List<Long> assetIds, BalanceOrdering balanceOrdering, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		String sql = "SELECT account, asset_id, IFNULL(balance, 0), asset_name FROM ";

		if (!addresses.isEmpty()) {
			sql += "(VALUES " + String.join(", ", Collections.nCopies(addresses.size(), "(?)")) + ") AS Accounts (account) ";
			sql += "CROSS JOIN Assets LEFT OUTER JOIN AccountBalances USING (asset_id, account) ";
		} else {
			// Simplier, no-address query
			sql += "AccountBalances NATURAL JOIN Assets ";
		}

		if (!assetIds.isEmpty())
			// longs are safe enough to use literally
			sql += "WHERE asset_id IN (" + String.join(", ", assetIds.stream().map(assetId -> assetId.toString()).collect(Collectors.toList())) + ") ";

		// For no-address queries, only return accounts with non-zero balance
		if (addresses.isEmpty()) {
			sql += assetIds.isEmpty() ? " WHERE " : " AND ";
			sql += "balance != 0 ";
		}

		String[] orderingColumns;
		switch (balanceOrdering) {
			case ACCOUNT_ASSET:
				orderingColumns = new String[] { "account", "asset_id" };
				break;

			case ASSET_ACCOUNT:
				orderingColumns = new String[] { "asset_id", "account" };
				break;

			case ASSET_BALANCE_ACCOUNT:
				orderingColumns = new String[] { "asset_id", "balance", "account" };
				break;

			default:
				throw new DataException(String.format("Unsupported asset balance result ordering: %s", balanceOrdering.name()));
		}

		if (reverse != null && reverse)
			orderingColumns = Arrays.stream(orderingColumns).map(column -> column + " DESC").toArray(size -> new String[size]);

		sql += "ORDER BY " + String.join(", ", orderingColumns);

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		String[] addressesArray = addresses.toArray(new String[addresses.size()]);
		List<AccountBalanceData> accountBalances = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, (Object[]) addressesArray)) {
			if (resultSet == null)
				return accountBalances;

			do {
				String address = resultSet.getString(1);
				long assetId = resultSet.getLong(2);
				BigDecimal balance = resultSet.getBigDecimal(3).setScale(8);
				String assetName = resultSet.getString(4);

				accountBalances.add(new AccountBalanceData(address, assetId, balance, assetName));
			} while (resultSet.next());

			return accountBalances;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset balances from repository", e);
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
