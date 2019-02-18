package org.qora.data.transaction;

import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.data.voting.PollOptionData;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreatePollTransactionData extends TransactionData {

	// Properties
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;

	// Constructors

	// For JAXB
	protected CreatePollTransactionData() {
		super(TransactionType.CREATE_POLL);
	}

	public CreatePollTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, String owner,
			String pollName, String description, List<PollOptionData> pollOptions, BigDecimal fee, byte[] signature) {
		super(Transaction.TransactionType.CREATE_POLL, timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
	}

	public CreatePollTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, String owner,
			String pollName, String description, List<PollOptionData> pollOptions, BigDecimal fee) {
		this(timestamp, txGroupId, reference, creatorPublicKey, owner, pollName, description, pollOptions, fee, null);
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
