package qora.assets;

import java.math.BigDecimal;
import java.util.List;

import data.assets.OrderData;
import repository.DataException;
import repository.Repository;

public class Order {

	// Properties
	private Repository repository;
	private OrderData orderData;

	// Constructors

	public Order(Repository repository, OrderData orderData) {
		this.repository = repository;
		this.orderData = orderData;
	}

	// Getters/Setters

	public OrderData getOrderData() {
		return this.orderData;
	}

	// More information

	public BigDecimal getAmountLeft() {
		return this.orderData.getAmount().subtract(this.orderData.getFulfilled());
	}

	public boolean isFulfilled() {
		return this.orderData.getFulfilled().compareTo(this.orderData.getAmount()) == 0;
	}

	// TODO
	// public List<Trade> getInitiatedTrades() {}

	// TODO
	// public boolean isConfirmed() {}

	// Navigation

	// XXX is this getInitiatedTrades() above?
	public List<Trade> getTrades() {
		// TODO

		return null;
	}

	// Processing

	public void process() throws DataException {
		// TODO
	}

	public void orphan() throws DataException {
		// TODO
	}

}
