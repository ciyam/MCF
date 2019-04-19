package org.qora.data.account;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.crypto.Crypto;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ProxyForgerData {

	// Properties
	private byte[] forgerPublicKey;
	private String recipient;
	private byte[] proxyPublicKey;
	private BigDecimal share;

	// Constructors

	// For JAXB
	protected ProxyForgerData() {
	}

	// Used when fetching from repository
	public ProxyForgerData(byte[] forgerPublicKey, String recipient, byte[] proxyPublicKey, BigDecimal share) {
		this.forgerPublicKey = forgerPublicKey;
		this.recipient = recipient;
		this.proxyPublicKey = proxyPublicKey;
		this.share = share;
	}

	// Getters / setters

	public byte[] getForgerPublicKey() {
		return this.forgerPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public byte[] getProxyPublicKey() {
		return this.proxyPublicKey;
	}

	public BigDecimal getShare() {
		return this.share;
	}

	@XmlElement(name = "forger")
	public String getForger() {
		return Crypto.toAddress(this.forgerPublicKey);
	}

}
