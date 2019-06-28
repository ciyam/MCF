package data.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import org.eclipse.persistence.oxm.annotations.XmlClassExtractor;

import api.TransactionClassExtractor;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import qora.crypto.Crypto;
import qora.transaction.Transaction.TransactionType;

/*
 * If you encounter an error like:
 * 
 * MessageBodyWriter not found for <some class>
 * 
 * then chances are that class is missing a no-argument constructor!
 */

@XmlClassExtractor(TransactionClassExtractor.class)
@XmlSeeAlso({ArbitraryTransactionData.class, ATTransactionData.class, BuyNameTransactionData.class, CancelOrderTransactionData.class, CancelSellNameTransactionData.class,
	CreateOrderTransactionData.class, CreatePollTransactionData.class, DeployATTransactionData.class, GenesisTransactionData.class, IssueAssetTransactionData.class,
	MessageTransactionData.class, MultiPaymentTransactionData.class, PaymentTransactionData.class, RegisterNameTransactionData.class, SellNameTransactionData.class,
	TransferAssetTransactionData.class, UpdateNameTransactionData.class, VoteOnPollTransactionData.class})
//All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class TransactionData {

	// Properties shared with all transaction types
	@Schema(accessMode = AccessMode.READ_ONLY, hidden = true)
	protected TransactionType type;
	@XmlTransient // represented in transaction-specific properties
	@Schema(hidden = true)
	protected byte[] creatorPublicKey;
	@Schema(description = "timestamp when transaction created, in milliseconds since unix epoch", example = "1545062012000")
	protected long timestamp;
	@Schema(description = "sender's last transaction ID", example = "47fw82McxnTQ8wtTS5A51Qojhg62b8px1rF3FhJp5a3etKeb5Y2DniL4Q6E7GbVCs6BAjHVe6sA4gTPxtYzng3AX")
	protected byte[] reference;
	@Schema(description = "fee for processing transaction", example = "1.0")
	protected BigDecimal fee;
	@Schema(accessMode = AccessMode.READ_ONLY, description = "signature for transaction's raw bytes, using sender's private key", example = "28u54WRcMfGujtQMZ9dNKFXVqucY7XfPihXAqPFsnx853NPUwfDJy1sMH5boCkahFgjUNYqc5fkduxdBhQTKgUsC")
	protected byte[] signature;

	// Constructors

	// For JAX-RS
	protected TransactionData() {
	}

	// For JAX-RS
	protected TransactionData(TransactionType type) {
		this.type = type;
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

	// JAXB special

	@XmlElement(name = "creatorAddress")
	protected String getCreatorAddress() {
		return Crypto.toAddress(this.creatorPublicKey);
	}

	@XmlTransient
	public void setCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
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
