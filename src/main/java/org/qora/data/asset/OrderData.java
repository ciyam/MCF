package org.qora.data.asset;

import java.math.BigDecimal;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.crypto.Crypto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderData implements Comparable<OrderData> {

	// Properties
	private byte[] orderId;
	private byte[] creatorPublicKey;

	@Schema(description = "asset on offer to give by order creator")
	private long haveAssetId;

	@Schema(description = "asset wanted to receive by order creator")
	private long wantAssetId;

	@Schema(description = "amount of highest-assetID asset to trade")
	private BigDecimal amount;

	@Schema(description = "price in lowest-assetID asset / highest-assetID asset")
	private BigDecimal price;

	@Schema(description = "how much of \"amount\" has traded")
	private BigDecimal fulfilled;

	private long timestamp;

	@Schema(description = "has this order been cancelled for further trades?")
	private boolean isClosed;

	@Schema(description = "has this order been fully traded?")
	private boolean isFulfilled;

	// Used by API - not always present

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String creator;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String haveAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String wantAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long amountAssetId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String amountAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String pricePair;

	// Constructors

	// Necessary for JAXB serialization
	protected OrderData() {
	}

	// Called before converting to JSON for API
	public void beforeMarshal(Marshaller m) {
		if (this.creator == null && this.creatorPublicKey != null)
			this.creator = Crypto.toAddress(this.creatorPublicKey);

		// If we don't have the extra asset name fields then we can't fill in the others
		if (this.haveAssetName == null)
			return;

		// 'old' pricing scheme is simpler so test for that first
		// XXX TODO

		// 'new' pricing scheme
		if (this.haveAssetId < this.wantAssetId) {
			this.amountAssetId = this.wantAssetId;
			this.amountAssetName = this.wantAssetName;
			this.pricePair = this.haveAssetName + "/" + this.wantAssetName;
		} else {
			this.amountAssetId = this.haveAssetId;
			this.amountAssetName = this.haveAssetName;
			this.pricePair = this.wantAssetName + "/" + this.haveAssetName;
		}
	}

	/** Constructs OrderData using data from repository, including optional API fields. */
	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal fulfilled, BigDecimal price, long timestamp,
			boolean isClosed, boolean isFulfilled, String haveAssetName, String wantAssetName) {
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

		this.haveAssetName = haveAssetName;
		this.wantAssetName = wantAssetName;
	}

	/** Constructs OrderData using data from repository, excluding optional API fields. */
	public OrderData(byte[] orderId, byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal fulfilled, BigDecimal price, long timestamp, boolean isClosed, boolean isFulfilled) {
		this(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price, timestamp, isClosed, isFulfilled, null, null);
	}

	/** Constructs OrderData using data typically received from network. */
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

	// Some JAXB/API-related getters

	public String getHaveAssetName() {
		return this.haveAssetName;
	}

	public String getWantAssetName() {
		return this.wantAssetName;
	}

	public long getAmountAssetId() {
		return this.amountAssetId;
	}

	public String getAmountAssetName() {
		return this.amountAssetName;
	}

	public String getPricePair() {
		return this.pricePair;
	}

	@Override
	public int compareTo(OrderData orderData) {
		// Compare using prices
		return this.price.compareTo(orderData.getPrice());
	}

}
