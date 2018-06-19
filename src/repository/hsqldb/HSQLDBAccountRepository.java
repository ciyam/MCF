package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.account.AccountBalanceData;
import data.account.AccountData;
import repository.AccountRepository;
import repository.DataException;

public class HSQLDBAccountRepository implements AccountRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAccountRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	public AccountData getAccount(String address) throws DataException {
		try {
			ResultSet resultSet = this.repository.checkedExecute("SELECT reference FROM Accounts WHERE account = ?", address);
			if (resultSet == null)
				return null;

			return new AccountData(address, resultSet.getBytes(1));
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account info from repository", e);
		}
	}

	public void save(AccountData accountData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Accounts");
		saveHelper.bind("account", accountData.getAddress()).bind("reference", accountData.getReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account info into repository", e);
		}
	}

	public AccountBalanceData getBalance(String address, long assetId) throws DataException {
		try {
			ResultSet resultSet = this.repository.checkedExecute("SELECT balance FROM AccountBalances WHERE account = ? and asset_id = ?", address, assetId);
			if (resultSet == null)
				return null;

			BigDecimal balance = resultSet.getBigDecimal(1).setScale(8);

			return new AccountBalanceData(address, assetId, balance);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account balance from repository", e);
		}
	}

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

	public void delete(String address, long assetId) throws DataException {
		try {
			this.repository.checkedExecute("DELETE FROM AccountBalances WHERE account = ? and asset_id = ?", address, assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete account balance from repository", e);
		}
	}

}
