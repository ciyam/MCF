package org.qora.repository;

import java.util.List;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;

public interface AccountRepository {

	// General account

	/** Returns all general information about account, e.g. public key, last reference, default group ID. */
	public AccountData getAccount(String address) throws DataException;

	/** Returns account's last reference or null if not set or account not found. */
	public byte[] getLastReference(String address) throws DataException;

	/** Returns account's default groupID or null if account not found. */
	public Integer getDefaultGroupId(String address) throws DataException;

	/**
	 * Ensures at least minimal account info in repository.
	 * <p>
	 * Saves account address, and public key if present.
	 */
	public void ensureAccount(AccountData accountData) throws DataException;

	/**
	 * Saves account's last reference, and public key if present, in repository.
	 * <p>
	 * Note: ignores other fields like default group ID.
	 */
	public void setLastReference(AccountData accountData) throws DataException;

	/**
	 * Saves account's default groupID, and public key if present, in repository.
	 * <p>
	 * Note: ignores other fields like last reference.
	 */
	public void setDefaultGroupId(AccountData accountData) throws DataException;

	public void delete(String address) throws DataException;

	// Account balances

	public AccountBalanceData getBalance(String address, long assetId) throws DataException;

	public enum BalanceOrdering {
		ASSET_BALANCE_ACCOUNT,
		ACCOUNT_ASSET,
		ASSET_ACCOUNT
	}

	public List<AccountBalanceData> getAssetBalances(List<String> addresses, List<Long> assetIds, BalanceOrdering balanceOrdering, Integer limit, Integer offset, Boolean reverse) throws DataException;

	public void save(AccountBalanceData accountBalanceData) throws DataException;

	public void delete(String address, long assetId) throws DataException;

}
