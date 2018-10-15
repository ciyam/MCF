package data.block;

import java.math.BigDecimal;

import com.google.common.primitives.Bytes;
import java.io.Serializable;

public class BlockData implements Serializable {

	private byte[] signature;
	private int version;
	private byte[] reference;
	private int transactionCount;
	private BigDecimal totalFees;
	private byte[] transactionsSignature;
	private int height;
	private long timestamp;
	private BigDecimal generatingBalance;
	private byte[] generatorPublicKey;
	private byte[] generatorSignature;
	private byte[] atBytes;
	private BigDecimal atFees;

	private BlockData() {} // necessary for JAX-RS serialization
	
	public BlockData(int version, byte[] reference, int transactionCount, BigDecimal totalFees, byte[] transactionsSignature, int height, long timestamp,
			BigDecimal generatingBalance, byte[] generatorPublicKey, byte[] generatorSignature, byte[] atBytes, BigDecimal atFees) {
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
		this.atBytes = atBytes;
		this.atFees = atFees;

		if (this.generatorSignature != null && this.transactionsSignature != null)
			this.signature = Bytes.concat(this.generatorSignature, this.transactionsSignature);
		else
			this.signature = null;
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

	public int getHeight() {
		return this.height;
	}

	public void setHeight(int height) {
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

	public byte[] getAtBytes() {
		return this.atBytes;
	}

	public BigDecimal getAtFees() {
		return this.atFees;
	}

}
