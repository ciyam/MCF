package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class BuyNameTransactionData extends TransactionData {

	// Properties
	@Schema(description = "buyer's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] buyerPublicKey;
	@Schema(description = "which name to buy", example = "my-name")
	private String name;
	@Schema(description = "selling price", example = "123.456")
	@XmlJavaTypeAdapter(
		type = BigDecimal.class,
		value = org.qora.api.BigDecimalTypeAdapter.class
	)
	private BigDecimal amount;
	@Schema(description = "seller's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String seller;
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] nameReference;

	// Constructors

	// For JAXB
	protected BuyNameTransactionData() {
		super(TransactionType.BUY_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.buyerPublicKey;
	}

	/** From repository */
	public BuyNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] buyerPublicKey, String name, BigDecimal amount, String seller,
			byte[] nameReference, BigDecimal fee,  ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.BUY_NAME, timestamp, txGroupId, reference, buyerPublicKey, fee, approvalStatus, height, signature);

		this.buyerPublicKey = buyerPublicKey;
		this.name = name;
		this.amount = amount;
		this.seller = seller;
		this.nameReference = nameReference;
	}

	/** From network/API */
	public BuyNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] buyerPublicKey, String name, BigDecimal amount, String seller,
			BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, buyerPublicKey, name, amount, seller, null, fee, null, null, signature);
	}

	/** New, unsigned */
	public BuyNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] buyerPublicKey, String name, BigDecimal amount, String seller, BigDecimal fee) {
		this(timestamp, txGroupId, reference, buyerPublicKey, name, amount, seller, fee, null);
	}

	// Getters / setters

	public byte[] getBuyerPublicKey() {
		return this.buyerPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public String getSeller() {
		return this.seller;
	}

	public byte[] getNameReference() {
		return this.nameReference;
	}

	public void setNameReference(byte[] nameReference) {
		this.nameReference = nameReference;
	}

}
