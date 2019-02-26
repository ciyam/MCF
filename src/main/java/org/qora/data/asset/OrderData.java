package org.qora.data.asset;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderData implements Comparable<OrderData> {

	// Properties
	private byte[] orderId;
	private byte[] creatorPublicKey;

	@Schema(description = "asset on offer to give by order creator")
	private long haveAssetId;

	@Schema(description = "asset wanted to receive by order creator")
	private long wantAssetId;

	@Schema(description = "amount of \"have\" asset to trade")
	private BigDecimal amount;

	@Schema(description = "amount of \"want\" asset to receive per unit of \"have\" asset traded")
	private BigDecimal price;

	@Schema(description = "how much \"have\" asset has traded")
	private BigDecimal fulfilled;

	private long timestamp;

	@Schema(description = "has this order been cancelled for further trades?")
	private boolean isClosed;

	@Schema(description = "has this order been fully traded?")
	private boolean isFulfilled;

	// Constructors

	// necessary for JAX-RS serialization
	protected OrderData() {
	}

	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal fulfilled, BigDecimal price,
			long timestamp, boolean isClosed, boolean isFulfilled) {
		this.orderId = orderId;
		this.creatorPublicKey = creatorPublicKey;
		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.fulfilled = fulfilled;
		this.price = price;
		this.timestamp = timestamp;
		this.isClosed = isClosed;
		this.isFulfilled = isFulfilled;
	}

	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, long timestamp) {
		this(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, BigDecimal.ZERO.setScale(8), price, timestamp, false, false);
	}

	// Getters/setters

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

	public boolean getIsClosed() {
		return this.isClosed;
	}

	public void setIsClosed(boolean isClosed) {
		this.isClosed = isClosed;
	}

	public boolean getIsFulfilled() {
		return this.isFulfilled;
	}

	public void setIsFulfilled(boolean isFulfilled) {
		this.isFulfilled = isFulfilled;
	}

	@Override
	public int compareTo(OrderData orderData) {
		// Compare using prices
		return this.price.compareTo(orderData.getPrice());
	}

}
