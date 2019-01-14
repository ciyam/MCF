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
	@Schema(description = "group name", example = "my-group")
	private String groupName;
	@Schema(description = "invitee's address", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String invitee;
	// No need to ever expose this via API
	@XmlTransient
	private byte[] groupReference;

	// Constructors

	// For JAX-RS
	protected CancelGroupInviteTransactionData() {
		super(TransactionType.CANCEL_GROUP_INVITE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public CancelGroupInviteTransactionData(byte[] adminPublicKey, String groupName, String invitee, byte[] groupReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CANCEL_GROUP_INVITE, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupName = groupName;
		this.invitee = invitee;
		this.groupReference = groupReference;
	}

	public CancelGroupInviteTransactionData(byte[] adminPublicKey, String groupName, String invitee, byte[] groupReference, BigDecimal fee, long timestamp, byte[] reference) {
		this(adminPublicKey, groupName, invitee, groupReference, fee, timestamp, reference, null);
	}

	public CancelGroupInviteTransactionData(byte[] adminPublicKey, String groupName, String invitee, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(adminPublicKey, groupName, invitee, null, fee, timestamp, reference, signature);
	}

	public CancelGroupInviteTransactionData(byte[] adminPublicKey, String groupName, String invitee, BigDecimal fee, long timestamp, byte[] reference) {
		this(adminPublicKey, groupName, invitee, null, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public String getGroupName() {
		return this.groupName;
	}

	public String getInvitee() {
		return this.invitee;
	}

	public byte[] getGroupReference() {
		return this.groupReference;
	}

	public void setGroupReference(byte[] groupReference) {
		this.groupReference = groupReference;
	}

}