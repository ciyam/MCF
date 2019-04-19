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
@Schema(
	allOf = {
		TransactionData.class
	}
)
public class GroupBanTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "admin's public key",
		example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP"
	)
	private byte[] adminPublicKey;
	@Schema(
		description = "group ID"
	)
	private int groupId;
	@Schema(
		description = "offender to ban from group",
		example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK"
	)
	private String offender;
	@Schema(
		description = "reason for ban"
	)
	private String reason;
	@Schema(
		description = "ban lifetime in seconds"
	)
	private int timeToLive;
	/** Reference to transaction that triggered membership. Could be JOIN_GROUP, GROUP_INVITE or UPDATE_GROUP transaction. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] memberReference;
	/** Reference to transaction that triggered adminship. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] adminReference;
	/** Reference to pending join-request or invite transaction that was deleted by this so it (invite/join-request) can be rebuilt during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] joinInviteReference;
	/** Offender's previous defaultGroupId, set only if this transaction changed it to NO_GROUP. */
	// No need to expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousGroupId;

	// Constructors

	// For JAXB
	protected GroupBanTransactionData() {
		super(TransactionType.GROUP_BAN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public GroupBanTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] adminPublicKey, int groupId, String member,
			String reason, int timeToLive, byte[] memberReference, byte[] adminReference, byte[] joinInviteReference, Integer previousGroupId, BigDecimal fee, byte[] signature) {
		super(TransactionType.GROUP_BAN, timestamp, txGroupId, reference, adminPublicKey, fee, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupId = groupId;
		this.offender = member;
		this.reason = reason;
		this.timeToLive = timeToLive;
		this.memberReference = memberReference;
		this.adminReference = adminReference;
		this.joinInviteReference = joinInviteReference;
		this.previousGroupId = previousGroupId;
	}

	/** Constructor typically used after deserialization */
	public GroupBanTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] adminPublicKey, int groupId, String offender, String reason,
			int timeToLive, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, adminPublicKey, groupId, offender, reason, timeToLive, null, null, null, null, fee, signature);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getOffender() {
		return this.offender;
	}

	public String getReason() {
		return this.reason;
	}

	public int getTimeToLive() {
		return this.timeToLive;
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

	public byte[] getJoinInviteReference() {
		return this.joinInviteReference;
	}

	public void setJoinInviteReference(byte[] reference) {
		this.joinInviteReference = reference;
	}

	public Integer getPreviousGroupId() {
		return this.previousGroupId;
	}

	public void setPreviousGroupId(Integer previousGroupId) {
		this.previousGroupId = previousGroupId;
	}

}
