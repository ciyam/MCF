package data.account;

public class AccountData {

	// Properties
	protected String address;
	protected byte[] reference;
	protected byte[] publicKey;

	// Constructors

	public AccountData(String address, byte[] reference, byte[] publicKey) {
		this.address = address;
		this.reference = reference;
		this.publicKey = publicKey;
	}

	public AccountData(String address) {
		this(address, null, null);
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

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public void setPublicKey(byte[] publicKey) {
		this.publicKey = publicKey;
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
