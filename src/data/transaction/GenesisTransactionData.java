package data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;
import qora.account.GenesisAccount;
import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class GenesisTransactionData extends TransactionData {

	// Properties
	private String recipient;
	private BigDecimal amount;

	// Constructors

	// For JAX-RS
	protected GenesisTransactionData() {
	}

	public GenesisTransactionData(String recipient, BigDecimal amount, long timestamp, byte[] signature) {
		// Zero fee
		super(TransactionType.GENESIS, BigDecimal.ZERO, GenesisAccount.PUBLIC_KEY, timestamp, null, signature);

		this.recipient = recipient;
		this.amount = amount;
	}

	public GenesisTransactionData(String recipient, BigDecimal amount, long timestamp) {
		this(recipient, amount, timestamp, null);
	}

	// Getters/Setters

	public String getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
