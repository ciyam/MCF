package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
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

	// For JAX-RS
	protected BuyNameTransactionData() {
		super(TransactionType.BUY_NAME);
	}

	public BuyNameTransactionData(byte[] buyerPublicKey, String name, BigDecimal amount, String seller, byte[] nameReference, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.BUY_NAME, fee, buyerPublicKey, timestamp, reference, signature);

		this.buyerPublicKey = buyerPublicKey;
		this.name = name;
		this.amount = amount;
		this.seller = seller;
		this.nameReference = nameReference;
	}

	public BuyNameTransactionData(byte[] buyerPublicKey, String name, BigDecimal amount, String seller, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		this(buyerPublicKey, name, amount, seller, null, fee, timestamp, reference, signature);
	}

	public BuyNameTransactionData(byte[] buyerPublicKey, String name, BigDecimal amount, String seller, byte[] nameReference, BigDecimal fee, long timestamp,
			byte[] reference) {
		this(buyerPublicKey, name, amount, seller, nameReference, fee, timestamp, reference, null);
	}

	public BuyNameTransactionData(byte[] buyerPublicKey, String name, BigDecimal amount, String seller, BigDecimal fee, long timestamp, byte[] reference) {
		this(buyerPublicKey, name, amount, seller, null, fee, timestamp, reference, null);
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
