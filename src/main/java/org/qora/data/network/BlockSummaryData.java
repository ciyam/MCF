package org.qora.data.network;

import org.qora.data.block.BlockData;

public class BlockSummaryData {

	// Properties
	private int height;
	private byte[] signature;
	private byte[] generatorPublicKey;

	// Constructors
	public BlockSummaryData(int height, byte[] signature, byte[] generatorPublicKey) {
		this.height = height;
		this.signature = signature;
		this.generatorPublicKey = generatorPublicKey;
	}

	public BlockSummaryData(BlockData blockData) {
		this(blockData.getHeight(), blockData.getSignature(), blockData.getGeneratorPublicKey());
	}

	// Getters / setters

	public int getHeight() {
		return this.height;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getGeneratorPublicKey() {
		return this.generatorPublicKey;
	}

}
