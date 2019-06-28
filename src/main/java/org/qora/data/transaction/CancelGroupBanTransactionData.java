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
public class CancelGroupBanTransactionData extends TransactionData {

	// Properties
	@Schema(description = "admin's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] adminPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "member to unban from group", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String member;
	/** Reference to GROUP_BAN transaction, used to rebuild ban during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] banReference;

	// Constructors

	// For JAX-RS
	protected CancelGroupBanTransactionData() {
		super(TransactionType.CANCEL_GROUP_BAN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	public CancelGroupBanTransactionData(byte[] adminPublicKey, int groupId, String member, byte[] banReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CANCEL_GROUP_BAN, fee, adminPublicKey, timestamp, reference, signature);

		this.adminPublicKey = adminPublicKey;
		this.groupId = groupId;
		this.member = member;
		this.banReference = banReference;
	}

	/** Constructor typically used after deserialization */
	public CancelGroupBanTransactionData(byte[] adminPublicKey, int groupId, String member, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(adminPublicKey, groupId, member, null, fee, timestamp, reference, signature);
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

	public byte[] getBanReference() {
		return this.banReference;
	}

	public void setBanReference(byte[] banReference) {
		this.banReference = banReference;
	}

}
