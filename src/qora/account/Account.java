package qora.account;

import java.math.BigDecimal;

import data.account.AccountBalanceData;
import data.account.AccountData;
import repository.DataException;
import repository.Repository;

public class Account {

	public static final int ADDRESS_LENGTH = 25;

	protected Repository repository;
	protected AccountData accountData;

	protected Account() {
	}

	public Account(Repository repository, String address) throws DataException {
		this.repository = repository;
		this.accountData = new AccountData(address);
	}

	public String getAddress() {
		return this.accountData.getAddress();
	}

	// Balance manipulations - assetId is 0 for QORA

	public BigDecimal getBalance(long assetId, int confirmations) {
		// TODO
		return null;
	}

	public BigDecimal getConfirmedBalance(long assetId) throws DataException {
		AccountBalanceData accountBalanceData = this.repository.getAccountRepository().getBalance(this.accountData.getAddress(), assetId);
		if (accountBalanceData == null)
			return BigDecimal.ZERO.setScale(8);

		return accountBalanceData.getBalance();
	}

	public void setConfirmedBalance(long assetId, BigDecimal balance) throws DataException {
		AccountBalanceData accountBalanceData = new AccountBalanceData(this.accountData.getAddress(), assetId, balance); 
		this.repository.getAccountRepository().save(accountBalanceData);
	}

	public void deleteBalance(long assetId) throws DataException {
		this.repository.getAccountRepository().delete(this.accountData.getAddress(), assetId);
	}

	// Reference manipulations

	/**
	 * Fetch last reference for account.
	 * 
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws DataException
	 */
	public byte[] getLastReference() throws DataException {
		AccountData accountData = this.repository.getAccountRepository().getAccount(this.accountData.getAddress());
		if (accountData == null)
			return null;

		return accountData.getReference();
	}

	/**
	 * Set last reference for account.
	 * 
	 * @param reference
	 *            -- null allowed
	 * @throws DataException 
	 */
	public void setLastReference(byte[] reference) throws DataException {
		this.repository.getAccountRepository().save(accountData);
	}

}
