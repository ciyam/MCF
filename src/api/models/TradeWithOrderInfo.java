package api.models;

import javax.xml.bind.annotation.XmlElement;

import data.assets.OrderData;
import data.assets.TradeData;
import io.swagger.v3.oas.annotations.media.Schema;
import repository.DataException;
import repository.Repository;

@Schema(description = "Asset trade, with order info")
public class TradeWithOrderInfo {

	@Schema(implementation = TradeData.class, name = "trade", title = "trade data")
	@XmlElement(name = "trade")
	public TradeData tradeData;

	@Schema(implementation = OrderData.class, name = "order", title = "order data")
	@XmlElement(name = "initiatingOrder")
	public OrderData initiatingOrderData;

	@Schema(implementation = OrderData.class, name = "order", title = "order data")
	@XmlElement(name = "targetOrder")
	public OrderData targetOrderData;

	// For JAX-RS
	protected TradeWithOrderInfo() {
	}

	public TradeWithOrderInfo(Repository repository, TradeData tradeData) throws DataException {
		this.tradeData = tradeData;

		this.initiatingOrderData = repository.getAssetRepository().fromOrderId(tradeData.getInitiator());
		this.targetOrderData = repository.getAssetRepository().fromOrderId(tradeData.getTarget());
	}

}
