package org.qora.api.model;

import java.math.BigDecimal;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.data.asset.OrderData;

@XmlAccessorType(XmlAccessType.NONE)
public class AggregatedOrder {

	// Not exposed to API
	private OrderData orderData;

	// Needed by JAXB for [un]marshalling
	protected AggregatedOrder() {
	}

	public void beforeMarshal(Marshaller m) {
		// OrderData needs to calculate values for us
		this.orderData.beforeMarshal(m);
	}

	public AggregatedOrder(OrderData orderData) {
		this.orderData = orderData;
	}

	@XmlElement(name = "price")
	public BigDecimal getPrice() {
		return this.orderData.getPrice();
	}

	@XmlElement(name = "unfulfilled")
	public BigDecimal getUnfulfilled() {
		return this.orderData.getAmount();
	}

	@XmlElement(name = "unfulfilledAssetId")
	public long getUnfulfilledAssetId() {
		return this.orderData.getAmountAssetId();
	}

	@XmlElement(name = "unfulfilledAssetName")
	public String getUnfulfilledAssetName() {
		return this.orderData.getAmountAssetName();
	}

	@XmlElement(name = "pricePair")
	public String getPricePair() {
		return this.orderData.getPricePair();
	}

}
