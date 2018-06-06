package data.account;

public class Account {

	// Properties
	protected String address;
	protected byte[] reference;

	// Constructors

	protected Account() {
	}

	public Account(String address) {
		this.address = address;
	}

	// Getters/Setters

	public String getAddress() {
		return this.address;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	// Comparison

	@Override
	public boolean equals(Object b) {
		if (!(b instanceof Account))
			return false;

		return this.getAddress().equals(((Account) b).getAddress());
	}

	@Override
	public int hashCode() {
		return this.getAddress().hashCode();
	}

}
