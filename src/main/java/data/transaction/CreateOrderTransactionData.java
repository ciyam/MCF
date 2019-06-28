package data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;
import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreateOrderTransactionData extends TransactionData {

	// Properties
	private long haveAssetId;
	private long wantAssetId;
	private BigDecimal amount;
	private BigDecimal price;

	// Constructors

	// For JAX-RS
	protected CreateOrderTransactionData() {
	}

	public CreateOrderTransactionData(byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, BigDecimal fee,
			long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CREATE_ASSET_ORDER, fee, creatorPublicKey, timestamp, reference, signature);

		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.price = price;
	}

	public CreateOrderTransactionData(byte[] creatorPublicKey, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, BigDecimal fee,
			long timestamp, byte[] reference) {
		this(creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, timestamp, reference, null);
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

}
