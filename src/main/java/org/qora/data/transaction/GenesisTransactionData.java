package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
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

	/** From repository (V2) */
	public GenesisTransactionData(BaseTransactionData baseTransactionData, String recipient, BigDecimal amount, long assetId) {
		// No groupID, null reference, zero fee, no approval required, height always 1
		// super(TransactionType.GENESIS, timestamp, Group.NO_GROUP, null, GenesisAccount.PUBLIC_KEY, BigDecimal.ZERO, ApprovalStatus.NOT_REQUIRED, 1, signature);
		super(TransactionType.GENESIS, baseTransactionData);

		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
	}

	/** From repository (V1, where asset locked to QORA) */
	public GenesisTransactionData(BaseTransactionData baseTransactionData, String recipient, BigDecimal amount) {
		this(baseTransactionData, recipient, amount, Asset.QORA);
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
