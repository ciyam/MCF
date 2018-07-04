package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class IssueAssetTransactionData extends TransactionData {

	// Properties
	// assetId can be null but assigned during save() or during load from repository
	private Long assetId = null;
	private byte[] issuerPublicKey;
	private String owner;
	private String assetName;
	private String description;
	private long quantity;
	private boolean isDivisible;

	// Constructors

	public IssueAssetTransactionData(Long assetId, byte[] issuerPublicKey, String owner, String assetName, String description, long quantity,
			boolean isDivisible, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.ISSUE_ASSET, fee, issuerPublicKey, timestamp, reference, signature);

		this.assetId = assetId;
		this.issuerPublicKey = issuerPublicKey;
		this.owner = owner;
		this.assetName = assetName;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
	}

	public IssueAssetTransactionData(byte[] issuerPublicKey, String owner, String assetName, String description, long quantity, boolean isDivisible,
			BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(null, issuerPublicKey, owner, assetName, description, quantity, isDivisible, fee, timestamp, reference, signature);
	}

	public IssueAssetTransactionData(byte[] issuerPublicKey, String owner, String assetName, String description, long quantity, boolean isDivisible,
			BigDecimal fee, long timestamp, byte[] reference) {
		this(null, issuerPublicKey, owner, assetName, description, quantity, isDivisible, fee, timestamp, reference, null);
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
