package data.voting;

import java.util.List;

import data.voting.PollOptionData;

public class PollData {

	// Properties
	private byte[] creatorPublicKey;
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;
	private long published;

	// Constructors

	public PollData(byte[] creatorPublicKey, String owner, String pollName, String description, List<PollOptionData> pollOptions, long published) {
		this.creatorPublicKey = creatorPublicKey;
		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
		this.published = published;
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

	public long getPublished() {
		return this.published;
	}

	public void setPublished(long published) {
		this.published = published;
	}

}
