package data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;
import qora.transaction.Transaction.TransactionType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class VoteOnPollTransactionData extends TransactionData {

	// Properties
	private byte[] voterPublicKey;
	private String pollName;
	private int optionIndex;
	private Integer previousOptionIndex;

	// Constructors

	// For JAX-RS
	protected VoteOnPollTransactionData() {
	}

	public VoteOnPollTransactionData(byte[] voterPublicKey, String pollName, int optionIndex, Integer previousOptionIndex, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		super(TransactionType.VOTE_ON_POLL, fee, voterPublicKey, timestamp, reference, signature);

		this.voterPublicKey = voterPublicKey;
		this.pollName = pollName;
		this.optionIndex = optionIndex;
		this.previousOptionIndex = previousOptionIndex;
	}

	public VoteOnPollTransactionData(byte[] voterPublicKey, String pollName, int optionIndex, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		this(voterPublicKey, pollName, optionIndex, null, fee, timestamp, reference, signature);
	}

	public VoteOnPollTransactionData(byte[] voterPublicKey, String pollName, int optionIndex, BigDecimal fee, long timestamp, byte[] reference) {
		this(voterPublicKey, pollName, optionIndex, null, fee, timestamp, reference, null);
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
