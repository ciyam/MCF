package qora.account;

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
}
