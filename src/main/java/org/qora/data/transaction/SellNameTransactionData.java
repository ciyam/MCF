package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class SellNameTransactionData extends TransactionData {

	// Properties
	@Schema(description = "owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "which name to sell", example = "my-name")
	private String name;
	@Schema(description = "selling price", example = "123.456")
	@XmlJavaTypeAdapter(
		type = BigDecimal.class,
		value = org.qora.api.BigDecimalTypeAdapter.class
	)
	private BigDecimal amount;

	// Constructors

	// For JAXB
	protected SellNameTransactionData() {
		super(TransactionType.SELL_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public SellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name, BigDecimal amount,
			BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.SELL_NAME, timestamp, txGroupId, reference, ownerPublicKey, fee, approvalStatus, height, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.name = name;
		this.amount = amount;
	}

	/** From network/API */
	public SellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name, BigDecimal amount, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, ownerPublicKey, name, amount, fee, null, null, signature);
	}

	/** New, unsigned */
	public SellNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] ownerPublicKey, String name, BigDecimal amount, BigDecimal fee) {
		this(timestamp, txGroupId, reference, ownerPublicKey, name, amount, fee, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
