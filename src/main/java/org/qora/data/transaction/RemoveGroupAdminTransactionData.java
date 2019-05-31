package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class RemoveGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "admin to demote", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String admin; 
	/** Reference to transaction that triggered adminship. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] adminReference;

	// Constructors

	// For JAXB
	protected RemoveGroupAdminTransactionData() {
		super(TransactionType.REMOVE_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public RemoveGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String admin, byte[] adminReference,
			BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.REMOVE_GROUP_ADMIN, timestamp, txGroupId, reference, ownerPublicKey, fee, approvalStatus, height, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.groupId = groupId;
		this.admin = admin;
		this.adminReference = adminReference;
	}

	/** From network/API */
	public RemoveGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String admin, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, ownerPublicKey, groupId, admin, null, fee, null, null, signature);
	}

	/** New, unsigned */
	public RemoveGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String admin, BigDecimal fee) {
		this(timestamp, txGroupId, reference, ownerPublicKey, groupId, admin, fee, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getAdmin() {
		return this.admin;
	}

	public byte[] getAdminReference() {
		return this.adminReference;
	}

	public void setAdminReference(byte[] adminReference) {
		this.adminReference = adminReference;
	}

}
