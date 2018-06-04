package qora.assets;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import qora.account.Account;
import utils.ParseException;

public class Order implements Comparable<Order> {

	// Properties
	private BigInteger id;
	private Account creator;
	private long haveAssetId;
	private long wantAssetId;
	private BigDecimal amount;
	private BigDecimal price;
	private long timestamp;

	// Other properties
	private BigDecimal fulfilled;

	// Constructors

	public Order(BigInteger id, Account creator, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, long timestamp) {
		this.id = id;
		this.creator = creator;
		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.price = price;
		this.timestamp = timestamp;

		this.fulfilled = BigDecimal.ZERO.setScale(8);
	}

	public Order(BigInteger id, Account creator, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal fulfilled, BigDecimal price,
			long timestamp) {
		this(id, creator, haveAssetId, wantAssetId, amount, price, timestamp);

		this.fulfilled = fulfilled;
	}

	// Getters/setters

	public BigInteger getId() {
		return this.id;
	}

	public Account getCreator() {
		return this.creator;
	}

	public long getHaveAssetId() {
		return this.haveAssetId;
	}

	public long getWantAssetId() {
		return this.wantAssetId;
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

	public BigDecimal getFulfilled() {
		return this.fulfilled;
	}

	public void setFulfilled(BigDecimal fulfilled) {
		this.fulfilled = fulfilled;
	}

	// More information

	public BigDecimal getAmountLeft() {
		return this.amount.subtract(this.fulfilled);
	}

	public boolean isFulfilled() {
		return this.fulfilled.compareTo(this.amount) == 0;
	}

	// TODO
	// public List<Trade> getInitiatedTrades() {}

	// TODO
	// public boolean isConfirmed() {}

	// Load/Save/Delete

	// Navigation

	// XXX is this getInitiatedTrades() above?
	public List<Trade> getTrades() {
		// TODO

		return null;
	}

	// Converters

	public static Order parse(byte[] data) throws ParseException {
		// TODO
		return null;
	}

	public byte[] toBytes() {
		// TODO

		return null;
	}

	// Processing

	// Other

	@Override
	public int compareTo(Order order) {
		// Compare using prices
		return this.price.compareTo(order.getPrice());
	}

	public Order copy() {
		try {
			return parse(this.toBytes());
		} catch (ParseException e) {
			return null;
		}
	}

}
