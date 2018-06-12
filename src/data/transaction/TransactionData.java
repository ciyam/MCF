package data.transaction;

import java.math.BigDecimal;

import qora.transaction.Transaction.TransactionType;

public abstract class TransactionData {

	// Properties shared with all transaction types
	protected TransactionType type;
	protected byte[] creatorPublicKey;
	protected long timestamp;
	protected byte[] reference;
	protected BigDecimal fee;
	protected byte[] signature;

	// Constructors

	public TransactionData(TransactionType type, BigDecimal fee, byte[] creatorPublicKey, long timestamp, byte[] reference, byte[] signature) {
		this.fee = fee;
		this.type = type;
		this.creatorPublicKey = creatorPublicKey;
		this.timestamp = timestamp;
		this.reference = reference;
		this.signature = signature;
	}

	public TransactionData(TransactionType type, BigDecimal fee, byte[] creatorPublicKey, long timestamp, byte[] reference) {
		this(type, fee, creatorPublicKey, timestamp, reference, null);
	}

	// Getters/setters

	public TransactionType getType() {
		return this.type;
	}

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public BigDecimal getFee() {
		return this.fee;
	}

	public byte[] getSignature() {
		return this.signature;
	}

}
