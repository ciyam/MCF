package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = {TransactionData.class})
public class ProxyForgingTransactionData extends TransactionData {

	private byte[] forgerPublicKey;
	private String recipient;
	private byte[] proxyPublicKey;
	private BigDecimal share;
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private BigDecimal previousShare;

	// Constructors

	// For JAXB
	protected ProxyForgingTransactionData() {
		super(TransactionType.PROXY_FORGING);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.forgerPublicKey;
	}


	public ProxyForgingTransactionData(long timestamp, int groupId, byte[] reference, byte[] forgerPublicKey, String recipient, byte[] proxyPublicKey, BigDecimal share, BigDecimal previousShare, BigDecimal fee, byte[] signature) {
		super(TransactionType.PROXY_FORGING, timestamp, groupId, reference, forgerPublicKey, fee, signature);
 
		this.forgerPublicKey = forgerPublicKey;
		this.recipient = recipient;
		this.proxyPublicKey = proxyPublicKey;
		this.share = share;
		this.previousShare = previousShare;
	}

	// Used in deserialization context
	public ProxyForgingTransactionData(long timestamp, int groupId, byte[] reference, byte[] forgerPublicKey, String recipient, byte[] proxyPublicKey, BigDecimal share, BigDecimal fee, byte[] signature) {
		this(timestamp, groupId, reference, forgerPublicKey, recipient, proxyPublicKey, share, null, fee, signature);
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

	public BigDecimal getPreviousShare() {
		return this.previousShare;
	}

	public void setPreviousShare(BigDecimal previousShare) {
		this.previousShare = previousShare;
	}

}
