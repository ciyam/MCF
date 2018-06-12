package data.block;

public class BlockTransactionData {

	// Properties
	protected byte[] blockSignature;
	protected int sequence;
	protected byte[] transactionSignature;

	// Constructors

	public BlockTransactionData(byte[] blockSignature, int sequence, byte[] transactionSignature) {
		this.blockSignature = blockSignature;
		this.sequence = sequence;
		this.transactionSignature = transactionSignature;
	}

	// Getters/setters

	public byte[] getBlockSignature() {
		return this.blockSignature;
	}

	public int getSequence() {
		return this.sequence;
	}

	public byte[] getTransactionSignature() {
		return this.transactionSignature;
	}

}
