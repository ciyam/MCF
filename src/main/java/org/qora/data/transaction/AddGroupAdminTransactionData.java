package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ADD_GROUP_ADMIN")
public class AddGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;

	@Schema(description = "group ID")
	private int groupId;

	@Schema(description = "member to promote to admin", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String member; 

	// Constructors

	// For JAXB
	protected AddGroupAdminTransactionData() {
		super(TransactionType.ADD_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public AddGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String member, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.ADD_GROUP_ADMIN, timestamp, txGroupId, reference, ownerPublicKey, fee, approvalStatus, height, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.groupId = groupId;
		this.member = member;
	}

	/** From network/API */
	public AddGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String member, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, ownerPublicKey, groupId, member, fee, null, null, signature);
	}

	/** New, unsigned */
	public AddGroupAdminTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, int groupId, String member, BigDecimal fee) {
		this(timestamp, txGroupId, reference, ownerPublicKey, groupId, member, fee, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getMember() {
		return this.member;
	}

}
