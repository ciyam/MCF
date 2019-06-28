package data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;
import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class BuyNameTransactionData extends TransactionData {

	// Properties
	private byte[] buyerPublicKey;
	private String name;
	private BigDecimal amount;
	private String seller;
	private byte[] nameReference;

	// Constructors

	// For JAX-RS
	protected BuyNameTransactionData() {
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
