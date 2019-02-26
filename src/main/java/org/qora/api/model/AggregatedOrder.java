package org.qora.api.model;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.data.asset.OrderData;

@XmlAccessorType(XmlAccessType.NONE)
public class AggregatedOrder {

	private OrderData orderData;

	protected AggregatedOrder() {
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

}
