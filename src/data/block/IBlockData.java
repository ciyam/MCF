package data.block;

import java.math.BigDecimal;

public interface IBlockData {
	public int getVersion();
	public byte[] getReference();
	public int getTransactionCount();
	public BigDecimal getTotalFees();
	public byte[] getTransactionsSignature();
	public int getHeight();
	public long getTimestamp();
	public BigDecimal getGeneratingBalance();
	public byte[] getGeneratorPublicKey();
	public byte[] getGeneratorSignature();
	public byte[] getAtBytes();
	public BigDecimal getAtFees();
}
