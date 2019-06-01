package org.qora.data.transaction;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
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

	// For JAXB
	protected UpdateNameTransactionData() {
		super(TransactionType.UPDATE_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public UpdateNameTransactionData(BaseTransactionData baseTransactionData, String newOwner, String name, String newData, byte[] nameReference) {
		super(TransactionType.UPDATE_NAME, baseTransactionData);

		this.ownerPublicKey = baseTransactionData.creatorPublicKey;
		this.newOwner = newOwner;
		this.name = name;
		this.newData = newData;
		this.nameReference = nameReference;
	}

	/** From network/API */
	public UpdateNameTransactionData(BaseTransactionData baseTransactionData, String newOwner, String name, String newData) {
		this(baseTransactionData, newOwner, name, newData, null);
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
