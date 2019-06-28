package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class VoteOnPollTransactionData extends TransactionData {

	// Properties
	private byte[] voterPublicKey;
	private String pollName;
	private int optionIndex;
	private Integer previousOptionIndex;

	// Constructors

	// For JAXB
	protected VoteOnPollTransactionData() {
		super(TransactionType.VOTE_ON_POLL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.voterPublicKey;
	}

	public VoteOnPollTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] voterPublicKey, String pollName, int optionIndex,
			Integer previousOptionIndex, BigDecimal fee, byte[] signature) {
		super(TransactionType.VOTE_ON_POLL, timestamp, txGroupId, reference, voterPublicKey, fee, signature);

		this.voterPublicKey = voterPublicKey;
		this.pollName = pollName;
		this.optionIndex = optionIndex;
		this.previousOptionIndex = previousOptionIndex;
	}

	public VoteOnPollTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] voterPublicKey, String pollName, int optionIndex,
			BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, voterPublicKey, pollName, optionIndex, null, fee, signature);
	}

	public VoteOnPollTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] voterPublicKey, String pollName, int optionIndex, BigDecimal fee) {
		this(timestamp, txGroupId, reference, voterPublicKey, pollName, optionIndex, null, fee, null);
	}

	// Getters / setters

	public byte[] getVoterPublicKey() {
		return this.voterPublicKey;
	}

	public String getPollName() {
		return this.pollName;
	}

	public int getOptionIndex() {
		return this.optionIndex;
	}

	public Integer getPreviousOptionIndex() {
		return this.previousOptionIndex;
	}

	public void setPreviousOptionIndex(Integer previousOptionIndex) {
		this.previousOptionIndex = previousOptionIndex;
	}

}
