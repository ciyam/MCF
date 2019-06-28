package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class DeployAtTransactionData extends TransactionData {

	// Properties
	private String name;
	private String description;
	private String ATType;
	private String tags;
	private byte[] creationBytes;
	private BigDecimal amount;
	private long assetId;
	private String ATAddress;

	// Constructors

	// For JAX-RS
	protected DeployAtTransactionData() {
	}

	public DeployAtTransactionData(String ATAddress, byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, long assetId, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.DEPLOY_AT, fee, creatorPublicKey, timestamp, reference, signature);

		this.name = name;
		this.description = description;
		this.ATType = ATType;
		this.tags = tags;
		this.amount = amount;
		this.assetId = assetId;
		this.creationBytes = creationBytes;
		this.ATAddress = ATAddress;
	}

	public DeployAtTransactionData(byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, long assetId, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(null, creatorPublicKey, name, description, ATType, tags, creationBytes, amount, assetId, fee, timestamp, reference, signature);
	}

	public DeployAtTransactionData(byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, long assetId, BigDecimal fee, long timestamp, byte[] reference) {
		this(null, creatorPublicKey, name, description, ATType, tags, creationBytes, amount, assetId, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getATType() {
		return this.ATType;
	}

	public String getTags() {
		return this.tags;
	}

	public byte[] getCreationBytes() {
		return this.creationBytes;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public long getAssetId() {
		return this.assetId;
	}

	public String getATAddress() {
		return this.ATAddress;
	}

	public void setATAddress(String ATAddress) {
		this.ATAddress = ATAddress;
	}

}
