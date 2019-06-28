package qora.voting;

import data.transaction.CreatePollTransactionData;
import data.voting.PollData;
import repository.DataException;
import repository.Repository;

public class Poll {

	// Properties
	private Repository repository;
	private PollData pollData;

	// Other useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DESCRIPTION_SIZE = 4000;
	public static final int MAX_OPTIONS = 100;

	// Constructors

	/**
	 * Construct Poll business object using poll data.
	 * 
	 * @param repository
	 * @param pollData
	 */
	public Poll(Repository repository, PollData pollData) {
		this.repository = repository;
		this.pollData = pollData;
	}

	/**
	 * Construct Poll business object using info from create poll transaction.
	 * 
	 * @param repository
	 * @param createPollTransactionData
	 */
	public Poll(Repository repository, CreatePollTransactionData createPollTransactionData) {
		this.repository = repository;
		this.pollData = new PollData(createPollTransactionData.getCreatorPublicKey(), createPollTransactionData.getOwner(),
				createPollTransactionData.getPollName(), createPollTransactionData.getDescription(), createPollTransactionData.getPollOptions(),
				createPollTransactionData.getTimestamp());
	}

	/**
	 * Construct Poll business object using existing poll from repository, identified by pollName.
	 * 
	 * @param repository
	 * @param pollName
	 * @throws DataException
	 */
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

	/**
	 * "Unpublish" poll, removing it from blockchain.
	 * <p>
	 * Typically used when orphaning create poll transaction.
	 * 
	 * @throws DataException
	 */
	public void unpublish() throws DataException {
		this.repository.getVotingRepository().delete(this.pollData.getPollName());
	}

}
