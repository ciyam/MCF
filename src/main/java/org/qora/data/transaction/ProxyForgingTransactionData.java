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

	@Schema(example = "forger_public_key")
	private byte[] forgerPublicKey;

	private String recipient;

	@Schema(example = "proxy_public_key")
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

	/** From repository */
	public ProxyForgingTransactionData(BaseTransactionData baseTransactionData,
			String recipient, byte[] proxyPublicKey, BigDecimal share, BigDecimal previousShare) {
		super(TransactionType.PROXY_FORGING, baseTransactionData);
 
		this.forgerPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;
		this.proxyPublicKey = proxyPublicKey;
		this.share = share;
		this.previousShare = previousShare;
	}

	/** From network/API */
	public ProxyForgingTransactionData(BaseTransactionData baseTransactionData,
			String recipient, byte[] proxyPublicKey, BigDecimal share) {
		this(baseTransactionData, recipient, proxyPublicKey, share, null);
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
