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
public class CancelGroupInviteTransactionData extends TransactionData {

	// Properties
	@Schema(description = "admin's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] adminPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "invitee's address", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String invitee;
	/** Reference to GROUP_INVITE transaction, used to rebuild invite during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] inviteReference;

	// Constructors

	// For JAX-RS
	protected CancelGroupInviteTransactionData() {
		super(TransactionType.CANCEL_GROUP_INVITE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public CancelGroupInviteTransactionData(byte[] adminPublicKey, int groupId, String invitee, byte[] inviteReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CANCEL_GROUP_INVITE, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupId = groupId;
		this.invitee = invitee;
		this.inviteReference = inviteReference;
	}

	/** Constructor typically used after deserialization */
	public CancelGroupInviteTransactionData(byte[] adminPublicKey, int groupId, String invitee, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(adminPublicKey, groupId, invitee, null, fee, timestamp, reference, signature);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getInvitee() {
		return this.invitee;
	}

	public byte[] getInviteReference() {
		return this.inviteReference;
	}

	public void setInviteReference(byte[] inviteReference) {
		this.inviteReference = inviteReference;
	}

}