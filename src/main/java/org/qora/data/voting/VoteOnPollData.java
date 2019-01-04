package org.qora.data.voting;

public class VoteOnPollData {

	// Properties
	private String pollName;
	private byte[] voterPublicKey;
	private int optionIndex;

	// Constructors

	public VoteOnPollData(String pollName, byte[] voterPublicKey, int optionIndex) {
		this.pollName = pollName;
		this.voterPublicKey = voterPublicKey;
		this.optionIndex = optionIndex;
	}

	// Getters/setters

	public String getPollName() {
		return this.pollName;
	}

	public byte[] getVoterPublicKey() {
		return this.voterPublicKey;
	}

	public int getOptionIndex() {
		return this.optionIndex;
	}

}
