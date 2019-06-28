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
public class JoinGroupTransactionData extends TransactionData {

	// Properties
	@Schema(description = "joiner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] joinerPublicKey;
	@Schema(description = "which group to join", example = "my-group")
	private int groupId;
	/** Reference to GROUP_INVITE transaction, used to rebuild invite during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] inviteReference;

	// Constructors

	// For JAX-RS
	protected JoinGroupTransactionData() {
		super(TransactionType.JOIN_GROUP);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.joinerPublicKey;
	}

	public JoinGroupTransactionData(byte[] joinerPublicKey, int groupId, byte[] inviteReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.JOIN_GROUP, fee, joinerPublicKey, timestamp, reference, signature);

		this.joinerPublicKey = joinerPublicKey;
		this.groupId = groupId;
		this.inviteReference = inviteReference;
	}

	/** Constructor typically used after deserialization */
	public JoinGroupTransactionData(byte[] joinerPublicKey, int groupId, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(joinerPublicKey, groupId, null, fee, timestamp, reference, signature);
	}

	// Getters / setters

	public byte[] getJoinerPublicKey() {
		return this.joinerPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public byte[] getInviteReference() {
		return this.inviteReference;
	}

	public void setInviteReference(byte[] inviteReference) {
		this.inviteReference = inviteReference;
	}

}
