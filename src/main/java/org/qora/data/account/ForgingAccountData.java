package org.qora.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.account.PrivateKeyAccount;
import org.qora.crypto.Crypto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ForgingAccountData {

	// Properties
	@Schema(hidden = true)
	@XmlTransient
	protected byte[] seed;

	// Not always present - used by API if not null
	@XmlTransient
	@Schema(hidden = true)
	protected byte[] publicKey;
	protected String proxiedBy;
	protected String proxiedFor;
	protected String address;

	// Constructors

	// For JAXB
	protected ForgingAccountData() {
	}

	public ForgingAccountData(byte[] seed) {
		this.seed = seed;
		this.publicKey = new PrivateKeyAccount(null, seed).getPublicKey();
	}

	public ForgingAccountData(byte[] seed, ProxyForgerData proxyForgerData) {
		this(seed);

		if (proxyForgerData != null) {
			this.proxiedFor = proxyForgerData.getRecipient();
			this.proxiedBy = Crypto.toAddress(proxyForgerData.getForgerPublicKey());
		} else {
			this.address = Crypto.toAddress(this.publicKey);
		}
	}

	// Getters/Setters

	public byte[] getSeed() {
		return this.seed;
	}

	@XmlElement(name = "publicKey")
	@Schema(accessMode = AccessMode.READ_ONLY)
	public byte[] getPublicKey() {
		return this.publicKey;
	}

}
