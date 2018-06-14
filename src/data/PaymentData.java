package data;

import java.math.BigDecimal;

public class PaymentData {

	// Properties
	protected String recipient;
	protected long assetId;
	protected BigDecimal amount;

	// Constructors

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
