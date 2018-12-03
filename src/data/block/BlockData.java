package data.block;

import java.math.BigDecimal;

import com.google.common.primitives.Bytes;
import java.io.Serializable;

public class BlockData implements Serializable {

	private static final long serialVersionUID = -7678329659124664620L;

	private byte[] signature;
	private int version;
	private byte[] reference;
	private int transactionCount;
	private BigDecimal totalFees;
	private byte[] transactionsSignature;
	private Integer height;
	private long timestamp;
	private BigDecimal generatingBalance;
	private byte[] generatorPublicKey;
	private byte[] generatorSignature;
	private int atCount;
	private BigDecimal atFees;

	// necessary for JAX-RS serialization
	@SuppressWarnings("unused")
	private BlockData() {
	}

	public BlockData(int version, byte[] reference, int transactionCount, BigDecimal totalFees, byte[] transactionsSignature, Integer height, long timestamp,
			BigDecimal generatingBalance, byte[] generatorPublicKey, byte[] generatorSignature, int atCount, BigDecimal atFees) {
		this.version = version;
		this.reference = reference;
		this.transactionCount = transactionCount;
		this.totalFees = totalFees;
		this.transactionsSignature = transactionsSignature;
		this.height = height;
		this.timestamp = timestamp;
		this.generatingBalance = generatingBalance;
		this.generatorPublicKey = generatorPublicKey;
		this.generatorSignature = generatorSignature;
		this.atCount = atCount;
		this.atFees = atFees;

		if (this.generatorSignature != null && this.transactionsSignature != null)
			this.signature = Bytes.concat(this.generatorSignature, this.transactionsSignature);
		else
			this.signature = null;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public int getVersion() {
		return this.version;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public int getTransactionCount() {
		return this.transactionCount;
	}

	public void setTransactionCount(int transactionCount) {
		this.transactionCount = transactionCount;
	}

	public BigDecimal getTotalFees() {
		return this.totalFees;
	}

	public void setTotalFees(BigDecimal totalFees) {
		this.totalFees = totalFees;
	}

	public byte[] getTransactionsSignature() {
		return this.transactionsSignature;
	}

	public void setTransactionsSignature(byte[] transactionsSignature) {
		this.transactionsSignature = transactionsSignature;
	}

	public Integer getHeight() {
		return this.height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public BigDecimal getGeneratingBalance() {
		return this.generatingBalance;
	}

	public byte[] getGeneratorPublicKey() {
		return this.generatorPublicKey;
	}

	public byte[] getGeneratorSignature() {
		return this.generatorSignature;
	}

	public void setGeneratorSignature(byte[] generatorSignature) {
		this.generatorSignature = generatorSignature;
	}

	public int getATCount() {
		return this.atCount;
	}

	public void setATCount(int atCount) {
		this.atCount = atCount;
	}

	public BigDecimal getATFees() {
		return this.atFees;
	}

	public void setATFees(BigDecimal atFees) {
		this.atFees = atFees;
	}

}
