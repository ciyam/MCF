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
public class GroupBanTransactionData extends TransactionData {

	// Properties
	@Schema(description = "admin's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] adminPublicKey;
	@Schema(description = "group name", example = "my-group")
	private String groupName;
	@Schema(description = "offender to ban from group", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String offender; 
	@Schema(description = "reason for ban")
	private String reason;
	@Schema(description = "ban lifetime in seconds")
	private int timeToLive;
	// No need to ever expose this via API
	@XmlTransient
	private byte[] memberReference;
	// No need to ever expose this via API
	@XmlTransient
	private byte[] adminReference;

	// Constructors

	// For JAX-RS
	protected GroupBanTransactionData() {
		super(TransactionType.GROUP_BAN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public GroupBanTransactionData(byte[] adminPublicKey, String groupName, String member, String reason, int timeToLive, byte[] memberReference, byte[] adminReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.GROUP_BAN, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupName = groupName;
		this.offender = member;
		this.reason = reason;
		this.timeToLive = timeToLive;
		this.memberReference = memberReference;
		this.adminReference = adminReference;
	}

	public GroupBanTransactionData(byte[] adminPublicKey, String groupName, String offender, String reason, int timeToLive, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(adminPublicKey, groupName, offender, reason, timeToLive, null, null, fee, timestamp, reference, signature);
	}

	public GroupBanTransactionData(byte[] adminPublicKey, String groupName, String offender, String reason, int timeToLive, byte[] memberReference, byte[] adminReference, BigDecimal fee, long timestamp, byte[] reference) {
		this(adminPublicKey, groupName, offender, reason, timeToLive, memberReference, adminReference, fee, timestamp, reference, null);
	}

	public GroupBanTransactionData(byte[] adminPublicKey, String groupName, String offender, String reason, int timeToLive, BigDecimal fee, long timestamp, byte[] reference) {
		this(adminPublicKey, groupName, offender, reason, timeToLive, null, null, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public String getGroupName() {
		return this.groupName;
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

}
