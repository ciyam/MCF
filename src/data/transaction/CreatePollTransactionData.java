package data.transaction;

import java.math.BigDecimal;
import java.util.List;

import data.voting.PollOptionData;
import qora.transaction.Transaction;

public class CreatePollTransactionData extends TransactionData {

	// Properties
	private byte[] creatorPublicKey;
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;

	// Constructors

	public CreatePollTransactionData(byte[] creatorPublicKey, String owner, String pollName, String description, List<PollOptionData> pollOptions,
			BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.CREATE_POLL, fee, creatorPublicKey, timestamp, reference, signature);

		this.creatorPublicKey = creatorPublicKey;
		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
	}

	// Getters/setters

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	public String getOwner() {
		return this.owner;
	}

	public String getPollName() {
		return this.pollName;
	}

	public String getDescription() {
		return this.description;
	}

	public List<PollOptionData> getPollOptions() {
		return this.pollOptions;
	}

}
