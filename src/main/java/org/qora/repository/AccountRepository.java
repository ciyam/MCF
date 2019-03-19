package org.qora.repository;

import java.util.List;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.data.account.ProxyForgerData;

public interface AccountRepository {

	// General account

	/** Returns all general information about account, e.g. public key, last reference, default group ID. */
	public AccountData getAccount(String address) throws DataException;

	/** Returns account's last reference or null if not set or account not found. */
	public byte[] getLastReference(String address) throws DataException;

	/** Returns account's default groupID or null if account not found. */
	public Integer getDefaultGroupId(String address) throws DataException;

	/** Returns account's flags or null if account not found. */
	public Integer getFlags(String address) throws DataException;

	/** Returns number of accounts enabled to forge by given address. */
	public int countForgingAccountsEnabledByAddress(String address) throws DataException;

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

	/**
	 * Saves account's flags, and public key if present, in repository.
	 * <p>
	 * Note: ignores other fields like last reference, default groupID.
	 */
	public void setFlags(AccountData accountData) throws DataException;

	/**
	 * Saves account's forging enabler, and public key if present, in repository.
	 * <p>
	 * Note: ignores other fields like last reference, default groupID.
	 */
	public void setForgingEnabler(AccountData accountData) throws DataException;

	/** Delete account from repository. */
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

	// Proxy forging

	public ProxyForgerData getProxyForgeData(byte[] forgerPublicKey, String recipient) throws DataException;

	public ProxyForgerData getProxyForgeData(byte[] proxyPublicKey) throws DataException;

	public List<ProxyForgerData> findProxyAccounts(List<String> recipients, List<String> forgers, Integer limit, Integer offset, Boolean reverse) throws DataException;

	public void save(ProxyForgerData proxyForgerData) throws DataException;

	public void delete(byte[] forgerPublickey, String recipient) throws DataException;

}
