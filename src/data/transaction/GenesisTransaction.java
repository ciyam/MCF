package data.transaction;

import java.math.BigDecimal;

import data.account.Account;
import data.account.GenesisAccount;

public class GenesisTransaction extends Transaction {

	// Properties
	private Account recipient;
	private BigDecimal amount;

	// Constructors

	public GenesisTransaction(Account recipient, BigDecimal amount, long timestamp, byte[] signature) {
		super(TransactionType.GENESIS, BigDecimal.ZERO, new GenesisAccount(), timestamp, signature);

		this.recipient = recipient;
		this.amount = amount;
	}

	public GenesisTransaction(Account recipient, BigDecimal amount, long timestamp) {
		this(recipient, amount, timestamp, null);
	}

	// Getters/Setters

	public Account getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

}
