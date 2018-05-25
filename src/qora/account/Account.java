package qora.account;

import java.math.BigDecimal;
import java.sql.Connection;

public class Account {

	public static final int ADDRESS_LENGTH = 25;

	protected String address;

	protected Account() {
	}

	public Account(String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	@Override
	public boolean equals(Object b) {
		if (!(b instanceof Account))
			return false;

		return this.getAddress().equals(((Account) b).getAddress());
	}

	// Balance manipulations - "key" is asset ID, or 0 for QORA

	public BigDecimal getBalance(long key, int confirmations) {
		// TODO
		return null;
	}

	public BigDecimal getUnconfirmedBalance(long key) {
		// TODO
		return null;
	}

	public BigDecimal getConfirmedBalance(long key) {
		// TODO
		return null;
	}

	public void setConfirmedBalance(Connection connection, long key, BigDecimal amount) {
		// TODO
		return;
	}

	// Reference manipulations

	public byte[] getLastReference() {
		// TODO
		return null;
	}

	// pass null to remove
	public void setLastReference(Connection connection, byte[] reference) {
		// TODO
		return;
	}

}
