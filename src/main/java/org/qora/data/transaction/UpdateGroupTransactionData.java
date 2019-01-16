package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class UpdateGroupTransactionData extends TransactionData {

	// Properties
	@Schema(description = "owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "new owner's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String newOwner;
	@Schema(description = "which group to update", example = "my-group")
	private int groupId;
	@Schema(description = "replacement group description", example = "my group for accounts I like")
	private String newDescription;
	@Schema(description = "new group join policy", example = "true")
	private boolean newIsOpen;
	/** Reference to CREATE_GROUP or UPDATE_GROUP transaction, used to rebuild group during orphaning. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] groupReference;

	// Constructors

	// For JAX-RS
	protected UpdateGroupTransactionData() {
		super(TransactionType.UPDATE_GROUP);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	public UpdateGroupTransactionData(byte[] ownerPublicKey, int groupId, String newOwner, String newDescription, boolean newIsOpen, byte[] groupReference, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.UPDATE_GROUP, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.newOwner = newOwner;
		this.groupId = groupId;
		this.newDescription = newDescription;
		this.newIsOpen = newIsOpen;
		this.groupReference = groupReference;
	}

	/** Constructor typically used after deserialization */
	public UpdateGroupTransactionData(byte[] ownerPublicKey, int groupId, String newOwner, String newDescription, boolean newIsOpen, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		this(ownerPublicKey, groupId, newOwner, newDescription, newIsOpen, null, fee, timestamp, reference, signature);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getNewOwner() {
		return this.newOwner;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getNewDescription() {
		return this.newDescription;
	}

	public boolean getNewIsOpen() {
		return this.newIsOpen;
	}

	public byte[] getGroupReference() {
		return this.groupReference;
	}

	public void setGroupReference(byte[] groupReference) {
		this.groupReference = groupReference;
	}

}
