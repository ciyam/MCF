package org.qora.data.transaction;

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
	@Schema(description = "non-human-readable asset-related data, typically JSON", example = "{\"logo\": \"data:image/jpeg;base64,/9j/4AAQSkZJRgA==\"}")
	private String data;

	// Constructors

	// For JAXB
	protected IssueAssetTransactionData() {
		super(TransactionType.ISSUE_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		/*
		 *  If we're being constructed as part of the genesis block info inside blockchain config
		 *  and no specific issuer's public key is supplied
		 *  then use genesis account's public key.
		 */
		if (parent instanceof GenesisBlock.GenesisInfo && this.issuerPublicKey == null)
			this.issuerPublicKey = GenesisAccount.PUBLIC_KEY;

		this.creatorPublicKey = this.issuerPublicKey;
	}

	/** From repository */
	public IssueAssetTransactionData(BaseTransactionData baseTransactionData,
			Long assetId, String owner, String assetName, String description, long quantity, boolean isDivisible, String data) {
		super(TransactionType.ISSUE_ASSET, baseTransactionData);

		this.assetId = assetId;
		this.issuerPublicKey = baseTransactionData.creatorPublicKey;
		this.owner = owner;
		this.assetName = assetName;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.data = data;
	}

	/** From network/API */
	public IssueAssetTransactionData(BaseTransactionData baseTransactionData, String owner, String assetName, String description, long quantity, boolean isDivisible, String data) {
		this(baseTransactionData, null, owner, assetName, description, quantity, isDivisible, data);
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

	public String getData() {
		return this.data;
	}

}
