package org.qora.data.transaction;

import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.data.voting.PollOptionData;
import org.qora.transaction.Transaction;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreatePollTransactionData extends TransactionData {

	// Properties
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;

	// Constructors

	// For JAX-RS
	protected CreatePollTransactionData() {
	}

	public CreatePollTransactionData(byte[] creatorPublicKey, String owner, String pollName, String description, List<PollOptionData> pollOptions,
			BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(Transaction.TransactionType.CREATE_POLL, fee, creatorPublicKey, timestamp, reference, signature);

		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
	}

	public CreatePollTransactionData(byte[] creatorPublicKey, String owner, String pollName, String description, List<PollOptionData> pollOptions,
			BigDecimal fee, long timestamp, byte[] reference) {
		this(creatorPublicKey, owner, pollName, description, pollOptions, fee, timestamp, reference, null);
	}

	// Getters/setters

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
