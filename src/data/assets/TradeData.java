package data.assets;

import java.math.BigDecimal;

public class TradeData {

	// Properties
	private byte[] initiator;
	private byte[] target;
	private BigDecimal amount;
	private BigDecimal price;
	private long timestamp;

	// Constructors

	public TradeData(byte[] initiator, byte[] target, BigDecimal amount, BigDecimal price, long timestamp) {
		this.initiator = initiator;
		this.target = target;
		this.amount = amount;
		this.price = price;
		this.timestamp = timestamp;
	}

	// Getters/setters

	public byte[] getInitiator() {
		return this.initiator;
	}

	public byte[] getTarget() {
		return this.target;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

}
