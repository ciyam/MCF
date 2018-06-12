package data.transaction;

import java.math.BigDecimal;

import qora.account.GenesisAccount;
import qora.transaction.Transaction.TransactionType;

public class GenesisTransactionData extends TransactionData {

	// Properties
	private String recipient;
	private BigDecimal amount;

	// Constructors

	public GenesisTransactionData(String recipient, BigDecimal amount, long timestamp, byte[] signature) {
		// Zero fee
		super(TransactionType.GENESIS, BigDecimal.ZERO, GenesisAccount.PUBLIC_KEY, timestamp, signature);

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
