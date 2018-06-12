package data.account;

import java.math.BigDecimal;

public class AccountBalanceData {

	// Properties
	protected String address;
	protected long assetId;
	protected BigDecimal balance;

	// Constructors

	public AccountBalanceData(String address, long assetId, BigDecimal balance) {
		this.address = address;
		this.assetId = assetId;
		this.balance = balance;
	}

	// Getters/Setters

	public String getAddress() {
		return this.address;
	}

	public long getAssetId() {
		return this.assetId;
	}

	public BigDecimal getBalance() {
		return this.balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

}
