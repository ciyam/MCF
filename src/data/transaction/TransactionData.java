package data.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class TransactionData {

	// Properties shared with all transaction types
	protected TransactionType type;
	protected byte[] creatorPublicKey;
	protected long timestamp;
	protected byte[] reference;
	protected BigDecimal fee;
	protected byte[] signature;

	// Constructors

	// For JAX-RS
	protected TransactionData() {
	}

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

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	// Comparison

	@Override
	public int hashCode() {
		byte[] bytes = this.signature;

		// No signature? Use reference instead
		if (bytes == null)
			bytes = this.reference;

		return new BigInteger(bytes).intValue();
	}

	@Override
	public boolean equals(Object other) {
		// If we don't have a signature then fail
		if (this.signature == null)
			return false;

		if (!(other instanceof TransactionData))
			return false;

		TransactionData otherTransactionData = (TransactionData) other;

		// If other transactionData has no signature then fail
		if (otherTransactionData.signature == null)
			return false;

		return Arrays.equals(this.signature, otherTransactionData.signature);
	}

}
