package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class LeaveGroupTransactionData extends TransactionData {

	// Properties
	@Schema(description = "leaver's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] leaverPublicKey;
	@Schema(description = "which group to leave", example = "my-group")
	private int groupId;
	/** Reference to transaction that triggered membership. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] memberReference;
	/** Reference to transaction that triggered adminship. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] adminReference;
	/** Leaver's previous defaultGroupId, set only if this transaction changed it to NO_GROUP. */
	// No need to expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousGroupId;

	// Constructors

	// For JAXB
	protected LeaveGroupTransactionData() {
		super(TransactionType.LEAVE_GROUP);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.leaverPublicKey;
	}

	public LeaveGroupTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] leaverPublicKey, int groupId, byte[] memberReference, byte[] adminReference, Integer previousGroupId, 
			BigDecimal fee, byte[] signature) {
		super(TransactionType.LEAVE_GROUP, timestamp, txGroupId, reference, leaverPublicKey, fee, signature);

		this.leaverPublicKey = leaverPublicKey;
		this.groupId = groupId;
		this.memberReference = memberReference;
		this.adminReference = adminReference;
		this.previousGroupId = previousGroupId;
	}

	/** Constructor typically used after deserialization */
	public LeaveGroupTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] leaverPublicKey, int groupId, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, leaverPublicKey, groupId, null, null, null, fee, signature);
	}

	// Getters / setters

	public byte[] getLeaverPublicKey() {
		return this.leaverPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public byte[] getMemberReference() {
		return this.memberReference;
	}

	public void setMemberReference(byte[] memberReference) {
		this.memberReference = memberReference;
	}

	public byte[] getAdminReference() {
		return this.adminReference;
	}

	public void setAdminReference(byte[] adminReference) {
		this.adminReference = adminReference;
	}

	public Integer getPreviousGroupId() {
		return this.previousGroupId;
	}

	public void setPreviousGroupId(Integer previousGroupId) {
		this.previousGroupId = previousGroupId;
	}

}
