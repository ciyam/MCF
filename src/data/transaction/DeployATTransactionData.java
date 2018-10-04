package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public class DeployATTransactionData extends TransactionData {

	// Properties
	private String name;
	private String description;
	private String ATType;
	private String tags;
	private byte[] creationBytes;
	private BigDecimal amount;
	private String ATAddress;

	// Constructors

	public DeployATTransactionData(String ATAddress, byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.DEPLOY_AT, fee, creatorPublicKey, timestamp, reference, signature);

		this.name = name;
		this.description = description;
		this.ATType = ATType;
		this.tags = tags;
		this.amount = amount;
		this.creationBytes = creationBytes;
		this.ATAddress = ATAddress;
	}

	public DeployATTransactionData(byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(null, creatorPublicKey, name, description, ATType, tags, creationBytes, amount, fee, timestamp, reference, signature);
	}

	public DeployATTransactionData(byte[] creatorPublicKey, String name, String description, String ATType, String tags, byte[] creationBytes,
			BigDecimal amount, BigDecimal fee, long timestamp, byte[] reference) {
		this(null, creatorPublicKey, name, description, ATType, tags, creationBytes, amount, fee, timestamp, reference, null);
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

	public String getATAddress() {
		return this.ATAddress;
	}

	public void setATAddress(String ATAddress) {
		this.ATAddress = ATAddress;
	}

}
