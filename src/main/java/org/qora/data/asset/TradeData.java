package org.qora.data.asset;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class TradeData {

	// Properties
	@Schema(name = "initiatingOrderId", description = "ID of order that caused trade")
	@XmlElement(name = "initiatingOrderId")
	private byte[] initiator;

	@Schema(name = "targetOrderId", description = "ID of order that matched")
	@XmlElement(name = "targetOrderId")
	private byte[] target;

	@Schema(name = "targetAmount", description = "amount traded from target order")
	@XmlElement(name = "targetAmount")
	private BigDecimal amount;

	@Schema(name = "initiatorAmount", description = "amount traded from initiating order")
	@XmlElement(name = "initiatorAmount")
	private BigDecimal price;

	@Schema(description = "when trade happened")
	private long timestamp;

	// Constructors

	// necessary for JAX-RS serialization
	protected TradeData() {
	}

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
