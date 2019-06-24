package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.data.account.ForgingAccountData;
import org.qora.data.account.ProxyForgerData;
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
		String sql = "SELECT reference, public_key, default_group_id, flags, forging_enabler FROM Accounts WHERE account = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
			if (resultSet == null)
				return null;

			byte[] reference = resultSet.getBytes(1);
			byte[] publicKey = resultSet.getBytes(2);
			int defaultGroupId = resultSet.getInt(3);
			int flags = resultSet.getInt(4);
			String forgingEnabler = resultSet.getString(5);

			return new AccountData(address, reference, publicKey, defaultGroupId, flags, forgingEnabler);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account info from repository", e);
		}
	}

	@Override
	public byte[] getLastReference(String address) throws DataException {
		String sql = "SELECT reference FROM Accounts WHERE account = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
			if (resultSet == null)
				return null;

			return resultSet.getBytes(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's last reference from repository", e);
		}
	}

	@Override
	public Integer getDefaultGroupId(String address) throws DataException {
		String sql = "SELECT default_group_id FROM Accounts WHERE account = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
			if (resultSet == null)
				return null;

			// Column is NOT NULL so this should never implicitly convert to 0
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's default groupID from repository", e);
		}
	}

	@Override
	public Integer getFlags(String address) throws DataException {
		String sql = "SELECT flags FROM Accounts WHERE account = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
			if (resultSet == null)
				return null;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's flags from repository", e);
		}
	}

	@Override
	public boolean accountExists(String address) throws DataException {
		try {
			return this.repository.exists("Accounts", "account = ?", address);
		} catch (SQLException e) {
			throw new DataException("Unable to check for account in repository", e);
		}
	}

	@Override
	public int countForgingAccountsEnabledByAddress(String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT COUNT(*) FROM Accounts WHERE forging_enabler = ? LIMIT 1", address)) {
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to count forging accounts enabled in repository", e);
		}
	}

	@Override
	public void ensureAccount(AccountData accountData) throws DataException {
		byte[] publicKey = accountData.getPublicKey();
		String sql = "SELECT public_key FROM Accounts WHERE account = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, accountData.getAddress())) {
			if (resultSet != null) {
				// We know account record exists at this point.
				// If accountData has no public key then we're done.
				// If accountData's public key matches repository's public key then we're done.
				if (publicKey == null || Arrays.equals(resultSet.getBytes(1), publicKey))
					return;
			}

			// No record exists, or we have a public key to set
			HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

			saveHelper.bind("account", accountData.getAddress());

			if (publicKey != null)
				saveHelper.bind("public_key", publicKey);

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
	public void setFlags(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

		saveHelper.bind("account", accountData.getAddress()).bind("flags", accountData.getFlags());

		byte[] publicKey = accountData.getPublicKey();
		if (publicKey != null)
			saveHelper.bind("public_key", publicKey);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account's flags into repository", e);
		}
	}

	@Override
	public void setForgingEnabler(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");

		saveHelper.bind("account", accountData.getAddress()).bind("forging_enabler", accountData.getForgingEnabler());

		byte[] publicKey = accountData.getPublicKey();
		if (publicKey != null)
			saveHelper.bind("public_key", publicKey);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account's forging enabler into repository", e);
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
		String sql = "SELECT balance FROM AccountBalances WHERE account = ? AND asset_id = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address, assetId)) {
			if (resultSet == null)
				return null;

			BigDecimal balance = resultSet.getBigDecimal(1).setScale(8);

			return new AccountBalanceData(address, assetId, balance);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account balance from repository", e);
		}
	}

	@Override
	public List<AccountBalanceData> getAssetBalances(List<String> addresses, List<Long> assetIds, BalanceOrdering balanceOrdering, Boolean excludeZero,
			Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(1024);
		sql.append("SELECT account, asset_id, IFNULL(balance, 0), asset_name FROM ");

		if (!addresses.isEmpty()) {
			sql.append("(VALUES ");

			final int addressesSize = addresses.size();
			for (int ai = 0; ai < addressesSize; ++ai) {
				if (ai != 0)
					sql.append(", ");

				sql.append("(?)");
			}

			sql.append(") AS Accounts (account) ");
			sql.append("CROSS JOIN Assets LEFT OUTER JOIN AccountBalances USING (asset_id, account) ");
		} else {
			// Simplier, no-address query
			sql.append("AccountBalances NATURAL JOIN Assets ");
		}

		if (!assetIds.isEmpty()) {
			// longs are safe enough to use literally
			sql.append("WHERE asset_id IN (");

			final int assetIdsSize = assetIds.size();
			for (int ai = 0; ai < assetIdsSize; ++ai) {
				if (ai != 0)
					sql.append(", ");

				sql.append(assetIds.get(ai));
			}

			sql.append(") ");
		}

		// For no-address queries, or unless specifically requested, only return accounts with non-zero balance
		if (addresses.isEmpty() || (excludeZero != null && excludeZero == true)) {
			sql.append(assetIds.isEmpty() ? " WHERE " : " AND ");
			sql.append("balance != 0 ");
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

		sql.append("ORDER BY ");
		for (int oi = 0; oi < orderingColumns.length; ++oi) {
			if (oi != 0)
				sql.append(", ");

			sql.append(orderingColumns[oi]);
			if (reverse != null && reverse)
				sql.append(" DESC");
		}

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		String[] addressesArray = addresses.toArray(new String[addresses.size()]);
		List<AccountBalanceData> accountBalances = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), (Object[]) addressesArray)) {
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

	// Proxy forging

	@Override
	public ProxyForgerData getProxyForgeData(byte[] forgerPublicKey, String recipient) throws DataException {
		String sql = "SELECT proxy_public_key, share FROM ProxyForgers WHERE forger = ? AND recipient = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, forgerPublicKey, recipient)) {
			if (resultSet == null)
				return null;

			byte[] proxyPublicKey = resultSet.getBytes(1);
			BigDecimal share = resultSet.getBigDecimal(2);

			return new ProxyForgerData(forgerPublicKey, recipient, proxyPublicKey, share);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch proxy forge info from repository", e);
		}
	}

	@Override
	public ProxyForgerData getProxyForgeData(byte[] proxyPublicKey) throws DataException {
		String sql = "SELECT forger, recipient, share FROM ProxyForgers WHERE proxy_public_key = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, proxyPublicKey)) {
			if (resultSet == null)
				return null;

			byte[] forgerPublicKey = resultSet.getBytes(1);
			String recipient = resultSet.getString(2);
			BigDecimal share = resultSet.getBigDecimal(3);

			return new ProxyForgerData(forgerPublicKey, recipient, proxyPublicKey, share);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch proxy forge info from repository", e);
		}
	}

	@Override
	public boolean isProxyPublicKey(byte[] publicKey) throws DataException {
		try {
			return this.repository.exists("ProxyForgers", "proxy_public_key = ?", publicKey);
		} catch (SQLException e) {
			throw new DataException("Unable to check for proxy public key in repository", e);
		}
	}

	@Override
	public int countProxyAccounts(byte[] forgerPublicKey) throws DataException {
		String sql = "SELECT COUNT(*) FROM ProxyForgers WHERE forger = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, forgerPublicKey)) {
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to count proxy forging relationships in repository", e);
		}
	}

	@Override
	public List<ProxyForgerData> getProxyAccounts() throws DataException {
		String sql = "SELECT forger, recipient, share, proxy_public_key FROM ProxyForgers";

		List<ProxyForgerData> proxyAccounts = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return proxyAccounts;

			do {
				byte[] forgerPublicKey = resultSet.getBytes(1);
				String recipient = resultSet.getString(2);
				BigDecimal share = resultSet.getBigDecimal(3);
				byte[] proxyPublicKey = resultSet.getBytes(4);

				proxyAccounts.add(new ProxyForgerData(forgerPublicKey, recipient, proxyPublicKey, share));
			} while (resultSet.next());

			return proxyAccounts;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch proxy forge accounts from repository", e);
		}
	}

	@Override
	public List<ProxyForgerData> findProxyAccounts(List<String> recipients, List<String> forgers, List<String> involvedAddresses,
			Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(1024);
		sql.append("SELECT DISTINCT forger, recipient, share, proxy_public_key FROM ProxyForgers ");

		List<Object> args = new ArrayList<>();

		final boolean hasRecipients = recipients != null && !recipients.isEmpty();
		final boolean hasForgers = forgers != null && !forgers.isEmpty();
		final boolean hasInvolved = involvedAddresses != null && !involvedAddresses.isEmpty();

		if (hasForgers || hasInvolved)
			sql.append("JOIN Accounts ON Accounts.public_key = ProxyForgers.forger ");

		if (hasRecipients) {
			sql.append("JOIN (VALUES ");

			final int recipientsSize = recipients.size();
			for (int ri = 0; ri < recipientsSize; ++ri) {
				if (ri != 0)
					sql.append(", ");

				sql.append("(?)");
			}

			sql.append(") AS Recipients (address) ON ProxyForgers.recipient = Recipients.address ");
			args.addAll(recipients);
		}

		if (hasForgers) {
			sql.append("JOIN (VALUES ");

			final int forgersSize = forgers.size();
			for (int fi = 0; fi < forgersSize; ++fi) {
				if (fi != 0)
					sql.append(", ");

				sql.append("(?)");
			}

			sql.append(") AS Forgers (address) ON Accounts.account = Forgers.address ");
			args.addAll(forgers);
		}

		if (hasInvolved) {
			sql.append("JOIN (VALUES ");


			final int involvedAddressesSize = involvedAddresses.size();
			for (int iai = 0; iai < involvedAddressesSize; ++iai) {
				if (iai != 0)
					sql.append(", ");

				sql.append("(?)");
			}

			sql.append(") AS Involved (address) ON Involved.address IN (ProxyForgers.recipient, Accounts.account) ");
			args.addAll(involvedAddresses);
		}

		sql.append("ORDER BY recipient, share");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ProxyForgerData> proxyAccounts = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), args.toArray())) {
			if (resultSet == null)
				return proxyAccounts;

			do {
				byte[] forgerPublicKey = resultSet.getBytes(1);
				String recipient = resultSet.getString(2);
				BigDecimal share = resultSet.getBigDecimal(3);
				byte[] proxyPublicKey = resultSet.getBytes(4);

				proxyAccounts.add(new ProxyForgerData(forgerPublicKey, recipient, proxyPublicKey, share));
			} while (resultSet.next());

			return proxyAccounts;
		} catch (SQLException e) {
			throw new DataException("Unable to find proxy forge accounts in repository", e);
		}
	}

	@Override
	public void save(ProxyForgerData proxyForgerData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ProxyForgers");

		saveHelper.bind("forger", proxyForgerData.getForgerPublicKey()).bind("recipient", proxyForgerData.getRecipient())
				.bind("proxy_public_key", proxyForgerData.getProxyPublicKey()).bind("share", proxyForgerData.getShare());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save proxy forge info into repository", e);
		}
	}

	@Override
	public void delete(byte[] forgerPublickey, String recipient) throws DataException {
		try {
			this.repository.delete("ProxyForgers", "forger = ? and recipient = ?", forgerPublickey, recipient);
		} catch (SQLException e) {
			throw new DataException("Unable to delete proxy forge info from repository", e);
		}
	}

	// Forging accounts used by BlockGenerator

	public List<ForgingAccountData> getForgingAccounts() throws DataException {
		List<ForgingAccountData> forgingAccounts = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT forger_seed FROM ForgingAccounts")) {
			if (resultSet == null)
				return forgingAccounts;

			do {
				byte[] forgerSeed = resultSet.getBytes(1);

				forgingAccounts.add(new ForgingAccountData(forgerSeed));
			} while (resultSet.next());

			return forgingAccounts;
		} catch (SQLException e) {
			throw new DataException("Unable to find forging accounts in repository", e);
		}
	}

	public void save(ForgingAccountData forgingAccountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ForgingAccounts");

		saveHelper.bind("forger_seed", forgingAccountData.getSeed());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save forging account into repository", e);
		}
	}

	public int delete(byte[] forgingAccountSeed) throws DataException {
		try {
			return this.repository.delete("ForgingAccounts", "forger_seed = ?", forgingAccountSeed);
		} catch (SQLException e) {
			throw new DataException("Unable to delete forging account from repository", e);
		}
	}

}
