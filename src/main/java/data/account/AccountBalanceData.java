package data.account;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class AccountBalanceData {

	// Properties
	private String address;
	private long assetId;
	private BigDecimal balance;

	// Constructors

	// necessary for JAX-RS serialization
	@SuppressWarnings("unused")
	private AccountBalanceData() {
	}

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
