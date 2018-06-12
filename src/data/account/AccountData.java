package data.account;

public class AccountData {

	// Properties
	protected String address;
	protected byte[] reference;

	// Constructors

	public AccountData(String address, byte[] reference) {
		this.address = address;
		this.reference = reference;
	}

	public AccountData(String address) {
		this(address, null);
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
		if (!(b instanceof AccountData))
			return false;

		return this.getAddress().equals(((AccountData) b).getAddress());
	}

	@Override
	public int hashCode() {
		return this.getAddress().hashCode();
	}

}
