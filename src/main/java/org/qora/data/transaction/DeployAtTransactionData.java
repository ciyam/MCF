package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class DeployAtTransactionData extends TransactionData {

	// Properties
	private String name;
	private String description;
	private String aTType;
	private String tags;
	private byte[] creationBytes;
	private BigDecimal amount;
	private long assetId;
	private String aTAddress;

	// Constructors

	// For JAX-RS
	protected DeployAtTransactionData() {
		super(TransactionType.DEPLOY_AT);
	}

	/** From repository */
	public DeployAtTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, String aTAddress, String name, String description,
			String aTType, String tags, byte[] creationBytes, BigDecimal amount, long assetId, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.DEPLOY_AT, timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, height, signature);

		this.aTAddress = aTAddress;
		this.name = name;
		this.description = description;
		this.aTType = aTType;
		this.tags = tags;
		this.creationBytes = creationBytes;
		this.amount = amount;
		this.assetId = assetId;
	}

	/** From network/API */
	public DeployAtTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, String name, String description,
			String aTType, String tags, byte[] creationBytes, BigDecimal amount, long assetId, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, creatorPublicKey, null, name, description, aTType, tags, creationBytes, amount, assetId, fee, null, null, signature);
	}

	/** New, unsigned */
	public DeployAtTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, String name, String description,
			String aTType, String tags, byte[] creationBytes, BigDecimal amount, long assetId, BigDecimal fee) {
		this(timestamp, txGroupId, reference, creatorPublicKey, name, description, aTType, tags, creationBytes, amount, assetId, fee, null);
	}

	// Getters/Setters

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getAtType() {
		return this.aTType;
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

	public String getAtAddress() {
		return this.aTAddress;
	}

	public void setAtAddress(String AtAddress) {
		this.aTAddress = AtAddress;
	}

}
