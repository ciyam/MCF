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
@Schema(
	allOf = {
		TransactionData.class
	}
)
public class GroupKickTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "admin's public key",
		example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP"
	)
	private byte[] adminPublicKey;
	@Schema(
		description = "group name",
		example = "my-group"
	)
	private int groupId;
	@Schema(
		description = "member to kick from group",
		example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK"
	)
	private String member;
	@Schema(
		description = "reason for kick"
	)
	private String reason;
	/** Reference to transaction that triggered membership. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] memberReference;
	/** Reference to transaction that triggered adminship. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] adminReference;
	/** Reference to JOIN_GROUP transaction, used to rebuild this join request during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] joinReference;

	// Constructors

	// For JAX-RS
	protected GroupKickTransactionData() {
		super(TransactionType.GROUP_KICK);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public GroupKickTransactionData(byte[] adminPublicKey, int groupId, String member, String reason, byte[] memberReference, byte[] adminReference,
			byte[] joinReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.GROUP_KICK, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupId = groupId;
		this.member = member;
		this.reason = reason;
		this.memberReference = memberReference;
		this.adminReference = adminReference;
		this.joinReference = joinReference;
	}

	/** Constructor typically used after deserialization */
	public GroupKickTransactionData(byte[] adminPublicKey, int groupId, String member, String reason, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		this(adminPublicKey, groupId, member, reason, null, null, null, fee, timestamp, reference, signature);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getMember() {
		return this.member;
	}

	public String getReason() {
		return this.reason;
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

	public byte[] getJoinReference() {
		return this.joinReference;
	}

	public void setJoinReference(byte[] joinReference) {
		this.joinReference = joinReference;
	}

}
