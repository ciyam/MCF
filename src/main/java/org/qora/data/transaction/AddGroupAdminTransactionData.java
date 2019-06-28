package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class AddGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "member to promote to admin", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String member; 

	// Constructors

	// For JAX-RS
	protected AddGroupAdminTransactionData() {
		super(TransactionType.ADD_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	public AddGroupAdminTransactionData(byte[] ownerPublicKey, int groupId, String member, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.ADD_GROUP_ADMIN, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.groupId = groupId;
		this.member = member;
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
