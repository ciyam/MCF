package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = {TransactionData.class})
public class EnableForgingTransactionData extends TransactionData {

	private String target;

	// Constructors

	// For JAXB
	protected EnableForgingTransactionData() {
		super(TransactionType.ENABLE_FORGING);
	}

	public EnableForgingTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, String target, BigDecimal fee, byte[] signature) {
		super(TransactionType.ENABLE_FORGING, timestamp, groupId, reference, creatorPublicKey, fee, signature);
 
		this.target = target;
	}

	public EnableForgingTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, String target, BigDecimal fee) {
		this(timestamp, groupId, reference, creatorPublicKey, target, fee, null);
	}

	// Getters / setters

	public String getTarget() {
		return this.target;
	}

	// Re-expose to JAXB

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getEnableForgingCreatorPublicKey() {
		return super.getCreatorPublicKey();
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setEnableForgingCreatorPublicKey(byte[] creatorPublicKey) {
		super.setCreatorPublicKey(creatorPublicKey);
	}

}
