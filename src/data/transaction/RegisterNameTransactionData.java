package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class RegisterNameTransactionData extends TransactionData {

	// Properties
	private byte[] registrantPublicKey;
	private String owner;
	private String name;
	private String data;

	// Constructors

	public RegisterNameTransactionData(byte[] registrantPublicKey, String owner, String name, String data, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.REGISTER_NAME, fee, registrantPublicKey, timestamp, reference, signature);

		this.registrantPublicKey = registrantPublicKey;
		this.owner = owner;
		this.name = name;
		this.data = data;
	}

	public RegisterNameTransactionData(byte[] registrantPublicKey, String owner, String name, String data, BigDecimal fee, long timestamp, byte[] reference) {
		this(registrantPublicKey, owner, name, data, fee, timestamp, reference, null);
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
		return this.data;
	}

}
