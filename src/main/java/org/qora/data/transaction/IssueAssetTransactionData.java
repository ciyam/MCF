package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.account.GenesisAccount;
import org.qora.block.GenesisBlock;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ISSUE_ASSET")
public class IssueAssetTransactionData extends TransactionData {

	// Properties
	// assetId can be null but assigned during save() or during load from repository
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long assetId = null;
	@Schema(description = "asset issuer's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] issuerPublicKey;
	@Schema(description = "asset owner's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String owner;
	@Schema(description = "asset name", example = "GOLD")
	private String assetName;
	@Schema(description = "asset description", example = "Gold asset - 1 unit represents one 1kg of gold")
	private String description;
	@Schema(description = "total supply of asset in existence (integer)", example = "1000")
	private long quantity;
	@Schema(description = "whether asset quantities can be fractional", example = "true")
	private boolean isDivisible;

	// Constructors

	// For JAXB
	protected IssueAssetTransactionData() {
		super(TransactionType.ISSUE_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		if (parent instanceof GenesisBlock.GenesisInfo && this.issuerPublicKey == null)
			this.issuerPublicKey = GenesisAccount.PUBLIC_KEY;

		this.creatorPublicKey = this.issuerPublicKey;
	}

	public IssueAssetTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] issuerPublicKey, Long assetId, String owner,
			String assetName, String description, long quantity, boolean isDivisible, BigDecimal fee, byte[] signature) {
		super(TransactionType.ISSUE_ASSET, timestamp, txGroupId, reference, issuerPublicKey, fee, signature);

		this.assetId = assetId;
		this.issuerPublicKey = issuerPublicKey;
		this.owner = owner;
		this.assetName = assetName;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
	}

	public IssueAssetTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] issuerPublicKey, String owner, String assetName,
			String description, long quantity, boolean isDivisible, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, issuerPublicKey, null, owner, assetName, description, quantity, isDivisible, fee, signature);
	}

	public IssueAssetTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] issuerPublicKey, String owner, String assetName,
			String description, long quantity, boolean isDivisible, BigDecimal fee) {
		this(timestamp, txGroupId, reference, issuerPublicKey, null, owner, assetName, description, quantity, isDivisible, fee, null);
	}

	// Getters/Setters

	public Long getAssetId() {
		return this.assetId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public byte[] getIssuerPublicKey() {
		return this.issuerPublicKey;
	}

	public String getOwner() {
		return this.owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getAssetName() {
		return this.assetName;
	}

	public String getDescription() {
		return this.description;
	}

	public long getQuantity() {
		return this.quantity;
	}

	public boolean getIsDivisible() {
		return this.isDivisible;
	}

}
