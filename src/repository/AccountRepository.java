package repository;

import java.util.List;

import data.account.AccountBalanceData;
import data.account.AccountData;

public interface AccountRepository {

	// General account

	public void create(String address) throws DataException;

	public AccountData getAccount(String address) throws DataException;

	public void save(AccountData accountData) throws DataException;

	public void delete(String address) throws DataException;

	// Account balances

	public AccountBalanceData getBalance(String address, long assetId) throws DataException;

	public List<AccountBalanceData> getAllBalances(String address) throws DataException;

	public void save(AccountBalanceData accountBalanceData) throws DataException;

	public void delete(String address, long assetId) throws DataException;

}
