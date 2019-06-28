package org.qora.data;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentData {

	// Properties
	protected String recipient;
	protected long assetId;
	protected BigDecimal amount;

	// Constructors

	// For JAXB
	protected PaymentData() {
	}

	public PaymentData(String recipient, long assetId, BigDecimal amount) {
		this.recipient = recipient;
		this.assetId = assetId;
		this.amount = amount;
	}

	// Getters/setters

	public String getRecipient() {
		return this.recipient;
	}

	public long getAssetId() {
		return this.assetId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
