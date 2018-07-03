package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class UpdateNameTransactionData extends TransactionData {

	// Properties
	private byte[] ownerPublicKey;
	private String newOwner;
	private String name;
	private String newData;
	private byte[] nameReference;

	// Constructors

	public UpdateNameTransactionData(byte[] ownerPublicKey, String newOwner, String name, String newData, byte[] nameReference, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.UPDATE_NAME, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.newOwner = newOwner;
		this.name = name;
		this.newData = newData;
		this.nameReference = nameReference;
	}

	public UpdateNameTransactionData(byte[] ownerPublicKey, String newOwner, String name, String newData, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		this(ownerPublicKey, newOwner, name, newData, null, fee, timestamp, reference, signature);
	}

	public UpdateNameTransactionData(byte[] ownerPublicKey, String newOwner, String name, String newData, byte[] nameReference, BigDecimal fee, long timestamp,
			byte[] reference) {
		this(ownerPublicKey, newOwner, name, newData, nameReference, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getNewOwner() {
		return this.newOwner;
	}

	public String getName() {
		return this.name;
	}

	public String getNewData() {
		return this.newData;
	}

	public byte[] getNameReference() {
		return this.nameReference;
	}

	public void setNameReference(byte[] nameReference) {
		this.nameReference = nameReference;
	}

}
