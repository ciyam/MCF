package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.crypto.Crypto;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlockForgerSummary {

	// Properties

	public String generatorAddress;
	public int blockCount;

	public String forgedBy;
	public String forgedFor;

	// Constructors

	protected BlockForgerSummary() {
	}

	public BlockForgerSummary(byte[] generator, int blockCount, byte[] forger, String recipient) {
		this.generatorAddress = Crypto.toAddress(generator);
		this.blockCount = blockCount;

		if (recipient != null) {
			this.forgedBy = Crypto.toAddress(forger);
			this.forgedFor = recipient;
		}
	}

}
