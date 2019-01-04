package org.qora.api.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Asset order info, maybe including trades")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderWithTrades {

	@Schema(implementation = OrderData.class, name = "order", title = "order data")
	@XmlElement(name = "order")
	public OrderData orderData;

	List<TradeData> trades;

	// For JAX-RS
	protected OrderWithTrades() {
	}

	public OrderWithTrades(OrderData orderData, List<TradeData> trades) {
		this.orderData = orderData;
		this.trades = trades;
	}

}
