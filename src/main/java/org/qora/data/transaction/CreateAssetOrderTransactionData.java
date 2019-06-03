package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.block.BlockChain;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreateAssetOrderTransactionData extends TransactionData {

	// Properties
	@Schema(description = "ID of asset on offer to give by order creator", example = "1")
	private long haveAssetId;
	@Schema(description = "ID of asset wanted to receive by order creator", example = "0")
	private long wantAssetId;
	@Schema(description = "amount of highest-assetID asset to trade")
	private BigDecimal amount;
	@Schema(description = "price in lowest-assetID asset / highest-assetID asset")
	private BigDecimal price;

	// Used by API - not always present

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

	// For JAXB
	protected CreateAssetOrderTransactionData() {
		super(TransactionType.CREATE_ASSET_ORDER);
	}

	// Called before converting to JSON for API
	public void beforeMarshal(Marshaller m) {
		final boolean isNewPricing = this.timestamp > BlockChain.getInstance().getNewAssetPricingTimestamp();

		this.amountAssetId = (isNewPricing && this.haveAssetId < this.wantAssetId) ? this.wantAssetId : this.haveAssetId;

		// If we don't have the extra asset name fields then we can't fill in the others
		if (this.haveAssetName == null)
			return;

		if (isNewPricing) {
			// 'new' pricing scheme
			if (this.haveAssetId < this.wantAssetId) {
				this.amountAssetName = this.wantAssetName;
				this.pricePair = this.haveAssetName + "/" + this.wantAssetName;
			} else {
				this.amountAssetName = this.haveAssetName;
				this.pricePair = this.wantAssetName + "/" + this.haveAssetName;
			}
		} else {
			// 'old' pricing scheme is simpler
			this.amountAssetName = this.haveAssetName;
			this.pricePair = this.wantAssetName + "/" + this.haveAssetName;
		}
	}

	/** Constructs using data from repository, including optional asset names. */
	public CreateAssetOrderTransactionData(BaseTransactionData baseTransactionData,
			long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, String haveAssetName, String wantAssetName) {
		super(TransactionType.CREATE_ASSET_ORDER, baseTransactionData);

		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.price = price;

		this.haveAssetName = haveAssetName;
		this.wantAssetName = wantAssetName;
	}

	/** Constructor excluding optional asset names. */
	public CreateAssetOrderTransactionData(BaseTransactionData baseTransactionData, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price) {
		this(baseTransactionData, haveAssetId, wantAssetId, amount, price, null, null);
	}

	// Getters/Setters

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

	// Re-expose creatorPublicKey for this transaction type for JAXB
	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getOrderCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setOrderCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

}
