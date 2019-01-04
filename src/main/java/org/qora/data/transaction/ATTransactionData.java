package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.account.GenesisAccount;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
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

	// For JAX-RS
	protected ATTransactionData() {
	}

	public ATTransactionData(String atAddress, String recipient, BigDecimal amount, Long assetId, byte[] message, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.AT, fee, GenesisAccount.PUBLIC_KEY, timestamp, reference, signature);

		this.atAddress = atAddress;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
		this.message = message;
	}

	public ATTransactionData(String atAddress, String recipient, BigDecimal amount, Long assetId, byte[] message, BigDecimal fee, long timestamp,
			byte[] reference) {
		this(atAddress, recipient, amount, assetId, message, fee, timestamp, reference, null);
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
