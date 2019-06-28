package org.qora.repository;

import java.util.List;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;

public interface AccountRepository {

	// General account

	public void create(AccountData accountData) throws DataException;

	public AccountData getAccount(String address) throws DataException;

	public void save(AccountData accountData) throws DataException;

	public void delete(String address) throws DataException;

	// Account balances

	public AccountBalanceData getBalance(String address, long assetId) throws DataException;

	public List<AccountBalanceData> getAllBalances(String address) throws DataException;

	public List<AccountBalanceData> getAssetBalances(long assetId) throws DataException;

	public void save(AccountBalanceData accountBalanceData) throws DataException;

	public void delete(String address, long assetId) throws DataException;

}
