package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class UpdateNameTransactionData extends TransactionData {

	// Properties
	@Schema(description = "owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "new owner's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String newOwner;
	@Schema(description = "which name to update", example = "my-name")
	private String name;
	@Schema(description = "replacement simple name-related info in JSON format", example = "{ \"age\": 30 }")
	private String newData;
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] nameReference;

	// Constructors

	// For JAX-RS
	protected UpdateNameTransactionData() {
		super(TransactionType.UPDATE_NAME);
	}

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
