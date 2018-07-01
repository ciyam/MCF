package data.naming;

public class NameData {

	// Properties
	private byte[] registrantPublicKey;
	private String owner;
	private String name;
	private String value;
	private long registered;

	// Constructors

	public NameData(byte[] registrantPublicKey, String owner, String name, String value, long timestamp) {
		this.registrantPublicKey = registrantPublicKey;
		this.owner = owner;
		this.name = name;
		this.value = value;
		this.registered = timestamp;
	}

	// Getters / setters

	public byte[] getRegistrantPublicKey() {
		return this.registrantPublicKey;
	}

	public String getOwner() {
		return this.owner;
	}

	public String getName() {
		return this.name;
	}

	public String getData() {
		return this.value;
	}

	public long getRegistered() {
		return this.registered;
	}

}
