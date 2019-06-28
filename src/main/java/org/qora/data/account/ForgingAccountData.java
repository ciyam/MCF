package org.qora.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ForgingAccountData {

	// Properties
	protected byte[] seed;

	// Constructors

	// For JAXB
	protected ForgingAccountData() {
	}

	public ForgingAccountData(byte[] seed) {
		this.seed = seed;
	}

	// Getters/Setters

	public byte[] getSeed() {
		return this.seed;
	}

}
