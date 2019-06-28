package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.group.Group.ApprovalThreshold;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(
	allOf = {
		TransactionData.class
	}
)
public class UpdateGroupTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "owner's public key",
		example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP"
	)
	private byte[] ownerPublicKey;
	@Schema(
		description = "new owner's address",
		example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v"
	)
	private String newOwner;
	@Schema(
		description = "which group to update",
		example = "my-group"
	)
	private int groupId;
	@Schema(
		description = "replacement group description",
		example = "my group for accounts I like"
	)
	private String newDescription;
	@Schema(
		description = "new group join policy",
		example = "true"
	)
	private boolean newIsOpen;
	@Schema(
		description = "new group member transaction approval threshold"
	)
	private ApprovalThreshold newApprovalThreshold;
	@Schema(description = "new minimum block delay before approval takes effect")
	private int newMinimumBlockDelay;
	@Schema(description = "new maximum block delay before which transaction approval must be reached")
	private int newMaximumBlockDelay;
	/** Reference to CREATE_GROUP or UPDATE_GROUP transaction, used to rebuild group during orphaning. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(
		hidden = true
	)
	private byte[] groupReference;

	// Constructors

	// For JAXB
	protected UpdateGroupTransactionData() {
		super(TransactionType.UPDATE_GROUP);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	public UpdateGroupTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId,
			String newOwner, String newDescription, boolean newIsOpen, ApprovalThreshold newApprovalThreshold, int newMinimumBlockDelay, int newMaximumBlockDelay, byte[] groupReference, BigDecimal fee, byte[] signature) {
		super(TransactionType.UPDATE_GROUP, timestamp, txGroupId, reference, ownerPublicKey, fee, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.newOwner = newOwner;
		this.groupId = groupId;
		this.newDescription = newDescription;
		this.newIsOpen = newIsOpen;
		this.newApprovalThreshold = newApprovalThreshold;
		this.newMinimumBlockDelay = newMinimumBlockDelay;
		this.newMaximumBlockDelay = newMaximumBlockDelay;
		this.groupReference = groupReference;
	}

	/** Constructor typically used after deserialization */
	public UpdateGroupTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId,
			String newOwner, String newDescription, boolean newIsOpen, ApprovalThreshold newApprovalThreshold, int newMinimumBlockDelay, int newMaximumBlockDelay, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, ownerPublicKey, groupId, newOwner, newDescription, newIsOpen, newApprovalThreshold, newMinimumBlockDelay, newMaximumBlockDelay, null, fee, signature);
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

	public ApprovalThreshold getNewApprovalThreshold() {
		return this.newApprovalThreshold;
	}

	public int getNewMinimumBlockDelay() {
		return this.newMinimumBlockDelay;
	}

	public int getNewMaximumBlockDelay() {
		return this.newMaximumBlockDelay;
	}

	public byte[] getGroupReference() {
		return this.groupReference;
	}

	public void setGroupReference(byte[] groupReference) {
		this.groupReference = groupReference;
	}

}
