package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
public class SetGroupTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "account's new default groupID",
		example = "true"
	)
	private int defaultGroupId;
	/** Reference to previous defaultGroupId, used during orphaning. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(
		hidden = true
	)
	private Integer previousDefaultGroupId;

	// Constructors

	// For JAXB
	protected SetGroupTransactionData() {
		super(TransactionType.SET_GROUP);
	}

	public SetGroupTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, int defaultGroupId, Integer previousDefaultGroupId,
			BigDecimal fee, byte[] signature) {
		super(TransactionType.SET_GROUP, timestamp, groupId, reference, creatorPublicKey, fee, signature);

		this.defaultGroupId = defaultGroupId;
		this.previousDefaultGroupId = previousDefaultGroupId;
	}

	/** Constructor typically used after deserialization */
	public SetGroupTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, int defaultGroupId, BigDecimal fee,
			byte[] signature) {
		this(timestamp, groupId, reference, creatorPublicKey, defaultGroupId, null, fee, signature);
	}

	// Getters / setters

	public int getDefaultGroupId() {
		return this.defaultGroupId;
	}

	public Integer getPreviousDefaultGroupId() {
		return this.previousDefaultGroupId;
	}

	public void setPreviousDefaultGroupId(int previousDefaultGroupId) {
		this.previousDefaultGroupId = previousDefaultGroupId;
	}

	// Re-expose to JAXB

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getSetGroupCreatorPublicKey() {
		return super.getCreatorPublicKey();
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setSetGroupCreatorPublicKey(byte[] creatorPublicKey) {
		super.setCreatorPublicKey(creatorPublicKey);
	}

}
