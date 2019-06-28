package org.qora.data.asset;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class RecentTradeData {

	// Properties
	private long assetId;

	private long otherAssetId;

	private BigDecimal otherAmount;

	private BigDecimal amount;

	@Schema(
		description = "when trade happened"
	)
	private long timestamp;

	// Constructors

	// necessary for JAXB serialization
	protected RecentTradeData() {
	}

	public RecentTradeData(long assetId, long otherAssetId, BigDecimal otherAmount, BigDecimal amount, long timestamp) {
		this.assetId = assetId;
		this.otherAssetId = otherAssetId;
		this.otherAmount = otherAmount;
		this.amount = amount;
		this.timestamp = timestamp;
	}

	// Getters/setters

	public long getAssetId() {
		return this.assetId;
	}

	public long getOtherAssetId() {
		return this.otherAssetId;
	}

	public BigDecimal getOtherAmount() {
		return this.otherAmount;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

}
