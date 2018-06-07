package data.block;

import java.math.BigDecimal;

import qora.account.PublicKeyAccount;

public class Block implements IBlockData {
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

	public Block(int version, byte[] reference, int transactionCount, BigDecimal totalFees, byte[] transactionsSignature, 
			int height, long timestamp, BigDecimal generatingBalance, byte[] generatorPublicKey, byte[] generatorSignature,
			byte[] atBytes, BigDecimal atFees)
	{
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
	}

	public int getVersion() {
		return version;
	}

	public byte[] getReference() {
		return reference;
	}

	public int getTransactionCount() {
		return transactionCount;
	}

	public BigDecimal getTotalFees() {
		return totalFees;
	}

	public byte[] getTransactionsSignature() {
		return transactionsSignature;
	}

	public int getHeight() {
		return height;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public BigDecimal getGeneratingBalance() {
		return generatingBalance;
	}

	public byte[] getGeneratorPublicKey() {
		return generatorPublicKey;
	}

	public byte[] getGeneratorSignature() {
		return generatorSignature;
	}

	public byte[] getAtBytes() {
		return atBytes;
	}

	public BigDecimal getAtFees() {
		return atFees;
	}
}
