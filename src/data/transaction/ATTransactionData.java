package data.transaction;

import java.math.BigDecimal;

import qora.account.GenesisAccount;
import qora.transaction.Transaction.TransactionType;

public class ATTransactionData extends TransactionData {

	// Properties
	private String atAddress;
	private String recipient;
	private BigDecimal amount;
	private Long assetId;
	private byte[] message;

	// Constructors

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
