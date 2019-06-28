package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.account.GenesisAccount;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class ATTransactionData extends TransactionData {

	// Properties
	private String atAddress;
	private String recipient;
	private BigDecimal amount;
	private Long assetId;
	private byte[] message;

	// Constructors

	// For JAXB
	protected ATTransactionData() {
		super(TransactionType.AT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = GenesisAccount.PUBLIC_KEY;
	}

	/** From repository */
	public ATTransactionData(BaseTransactionData baseTransactionData, String atAddress, String recipient, BigDecimal amount, Long assetId, byte[] message) {
		super(TransactionType.AT, baseTransactionData);

		this.creatorPublicKey = GenesisAccount.PUBLIC_KEY;
		this.atAddress = atAddress;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
		this.message = message;
	}

	// Getters/Setters

	public String getATAddress() {
		return this.atAddress;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public byte[] getMessage() {
		return this.message;
	}

}
