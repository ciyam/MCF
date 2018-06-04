package qora.assets;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Trade {

	// Properties
	private BigInteger initiator;
	private BigInteger target;
	private BigDecimal amount;
	private BigDecimal price;
	private long timestamp;

	// Constructors

	public Trade(BigInteger initiator, BigInteger target, BigDecimal amount, BigDecimal price, long timestamp) {
		this.initiator = initiator;
		this.target = target;
		this.amount = amount;
		this.price = price;
		this.timestamp = timestamp;
	}

	// Getters/setters

	public BigInteger getInitiator() {
		return this.initiator;
	}

	public BigInteger getTarget() {
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
