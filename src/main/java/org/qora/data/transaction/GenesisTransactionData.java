package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.account.GenesisAccount;
import org.qora.asset.Asset;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(
	allOf = {
		TransactionData.class
	}
)
//JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("GENESIS")
public class GenesisTransactionData extends TransactionData {

	// Properties
	private String recipient;
	private BigDecimal amount;
	private long assetId;

	// Constructors

	// For JAXB
	protected GenesisTransactionData() {
		super(TransactionType.GENESIS);
	}

	public GenesisTransactionData(long timestamp, String recipient, BigDecimal amount, long assetId, byte[] signature) {
		// no groupID, Zero fee
		super(TransactionType.GENESIS, timestamp, 0, null, GenesisAccount.PUBLIC_KEY, BigDecimal.ZERO, signature);

		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
	}

	public GenesisTransactionData(long timestamp, String recipient, BigDecimal amount, byte[] signature) {
		this(timestamp, recipient, amount, Asset.QORA, signature);
	}

	public GenesisTransactionData(long timestamp, String recipient, BigDecimal amount, long assetId) {
		this(timestamp, recipient, amount, assetId, null);
	}

	public GenesisTransactionData(long timestamp, String recipient, BigDecimal amount) {
		this(timestamp, recipient, amount, Asset.QORA, null);
	}

	// Getters/Setters

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public long getAssetId() {
		return this.assetId;
	}

}
