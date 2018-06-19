package qora.voting;

import data.transaction.CreatePollTransactionData;
import data.voting.PollData;
import repository.DataException;
import repository.Repository;

public class Poll {

	// Properties
	private Repository repository;
	private PollData pollData;

	// Constructors

	public Poll(Repository repository, PollData pollData) {
		this.repository = repository;
		this.pollData = pollData;
	}

	/**
	 * Create Poll business object using info from create poll transaction.
	 * 
	 * @param repository
	 * @param createPollTransactionData
	 */
	public Poll(Repository repository, CreatePollTransactionData createPollTransactionData) {
		this.repository = repository;
		this.pollData = new PollData(createPollTransactionData.getCreatorPublicKey(), createPollTransactionData.getOwner(),
				createPollTransactionData.getPollName(), createPollTransactionData.getDescription(), createPollTransactionData.getPollOptions());
	}

	public Poll(Repository repository, String pollName) throws DataException {
		this.repository = repository;
		this.pollData = this.repository.getVotingRepository().fromPollName(pollName);
	}

	// Processing

	/**
	 * "Publish" poll to allow voting.
	 * 
	 * @throws DataException
	 */
	public void publish() throws DataException {
		this.repository.getVotingRepository().save(this.pollData);
	}

	public void unpublish() throws DataException {
		this.repository.getVotingRepository().delete(this.pollData.getPollName());
	}

}
