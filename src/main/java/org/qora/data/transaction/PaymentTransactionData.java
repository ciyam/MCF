package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema( allOf = { TransactionData.class } )
public class PaymentTransactionData extends TransactionData {

	// Properties
	@Schema(description = "sender's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] senderPublicKey;
	@Schema(description = "recipient's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String recipient;
	@Schema(description = "amount to send", example = "123.456")
	@XmlJavaTypeAdapter(
		type = BigDecimal.class,
		value = org.qora.api.BigDecimalTypeAdapter.class
	)
	private BigDecimal amount;

	// Constructors

	// For JAXB
	protected PaymentTransactionData() {
		super(TransactionType.PAYMENT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** From repository */
	public PaymentTransactionData(BaseTransactionData baseTransactionData, String recipient, BigDecimal amount) {
		super(TransactionType.PAYMENT, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;
		this.amount = amount;
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
