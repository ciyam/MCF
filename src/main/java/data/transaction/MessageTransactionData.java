package data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;
import qora.assets.Asset;
import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MessageTransactionData extends TransactionData {

	// Properties
	private int version;
	private byte[] senderPublicKey;
	private String recipient;
	private Long assetId;
	private BigDecimal amount;
	private byte[] data;
	private boolean isText;
	private boolean isEncrypted;

	// Constructors

	// For JAX-RS
	protected MessageTransactionData() {
	}

	public MessageTransactionData(int version, byte[] senderPublicKey, String recipient, Long assetId, BigDecimal amount, byte[] data, boolean isText,
			boolean isEncrypted, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.MESSAGE, fee, senderPublicKey, timestamp, reference, signature);

		this.version = version;
		this.senderPublicKey = senderPublicKey;
		this.recipient = recipient;

		if (assetId != null)
			this.assetId = assetId;
		else
			this.assetId = Asset.QORA;

		this.amount = amount;
		this.data = data;
		this.isText = isText;
		this.isEncrypted = isEncrypted;
	}

	public MessageTransactionData(int version, byte[] senderPublicKey, String recipient, Long assetId, BigDecimal amount, byte[] data, boolean isText,
			boolean isEncrypted, BigDecimal fee, long timestamp, byte[] reference) {
		this(version, senderPublicKey, recipient, assetId, amount, data, isText, isEncrypted, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public int getVersion() {
		return this.version;
	}

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public byte[] getData() {
		return this.data;
	}

	public boolean getIsText() {
		return this.isText;
	}

	public boolean getIsEncrypted() {
		return this.isEncrypted;
	}

}
