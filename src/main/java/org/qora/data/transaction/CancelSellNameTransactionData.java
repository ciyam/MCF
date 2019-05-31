package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelSellNameTransactionData extends TransactionData {

	// Properties
	@Schema(description = "owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "which name to cancel selling", example = "my-name")
	private String name;

	// Constructors

	// For JAXB
	protected CancelSellNameTransactionData() {
		super(TransactionType.CANCEL_SELL_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public CancelSellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name,
			BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.CANCEL_SELL_NAME, timestamp, txGroupId, reference, ownerPublicKey, fee, approvalStatus, height, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.name = name;
	}

	/** From network/API */
	public CancelSellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, ownerPublicKey, name, fee, null, null, signature);
	}

	/** New, unsigned */
	public CancelSellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name, BigDecimal fee) {
		this(timestamp, txGroupId, reference, ownerPublicKey, name, fee, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getName() {
		return this.name;
	}

}
