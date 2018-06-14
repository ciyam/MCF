package data.assets;

import java.math.BigDecimal;

public class OrderData implements Comparable<OrderData> {

	private byte[] orderId;
	private byte[] creatorPublicKey;
	private long haveAssetId;
	private long wantAssetId;
	private BigDecimal amount;
	private BigDecimal fulfilled;
	private BigDecimal price;
	private long timestamp;

	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal fulfilled, BigDecimal price,
			long timestamp) {
		this.orderId = orderId;
		this.creatorPublicKey = creatorPublicKey;
		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.fulfilled = fulfilled;
		this.price = price;
		this.timestamp = timestamp;
	}

	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, long timestamp) {
		this(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, BigDecimal.ZERO.setScale(8), price, timestamp);
	}

	public byte[] getOrderId() {
		return this.orderId;
	}

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
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

	public BigDecimal getFulfilled() {
		return this.fulfilled;
	}

	public void setFulfilled(BigDecimal fulfilled) {
		this.fulfilled = fulfilled;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	@Override
	public int compareTo(OrderData orderData) {
		// Compare using prices
		return this.price.compareTo(orderData.getPrice());
	}

}
