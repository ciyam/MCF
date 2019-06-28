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
public class GroupInviteTransactionData extends TransactionData {

	// Properties
	@Schema(description = "admin's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] adminPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "invitee's address", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String invitee;
	@Schema(description = "invitation lifetime in seconds")
	private int timeToLive;
	/** Reference to JOIN_GROUP transaction, used to rebuild this join request during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] joinReference;

	// Constructors

	// For JAX-RS
	protected GroupInviteTransactionData() {
		super(TransactionType.GROUP_INVITE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public GroupInviteTransactionData(byte[] adminPublicKey, int groupId, String invitee, int timeToLive, byte[] joinReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.GROUP_INVITE, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupId = groupId;
		this.invitee = invitee;
		this.timeToLive = timeToLive;
		this.joinReference = joinReference;
	}

	/** Constructor typically used after deserialization */
	public GroupInviteTransactionData(byte[] adminPublicKey, int groupId, String invitee, int timeToLive, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(adminPublicKey, groupId, invitee, timeToLive, null, fee, timestamp, reference, signature);
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

	public int getTimeToLive() {
		return this.timeToLive;
	}

	public byte[] getJoinReference() {
		return this.joinReference;
	}

	public void setJoinReference(byte[] joinReference) {
		this.joinReference = joinReference;
	}

}