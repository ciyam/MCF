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
public class JoinGroupTransactionData extends TransactionData {

	// Properties
	@Schema(description = "joiner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] joinerPublicKey;
	@Schema(description = "which group to update", example = "my-group")
	private String groupName;

	// Constructors

	// For JAX-RS
	protected JoinGroupTransactionData() {
		super(TransactionType.JOIN_GROUP);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.joinerPublicKey;
	}

	public JoinGroupTransactionData(byte[] joinerPublicKey, String groupName, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.JOIN_GROUP, fee, joinerPublicKey, timestamp, reference, signature);

		this.joinerPublicKey = joinerPublicKey;
		this.groupName = groupName;
	}

	public JoinGroupTransactionData(byte[] joinerPublicKey, String groupName, BigDecimal fee, long timestamp, byte[] reference) {
		this(joinerPublicKey, groupName, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getJoinerPublicKey() {
		return this.joinerPublicKey;
	}

	public String getGroupName() {
		return this.groupName;
	}

}
