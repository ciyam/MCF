package repository.hsqldb;

import data.voting.PollData;
import repository.VotingRepository;
import repository.DataException;

public class HSQLDBVotingRepository implements VotingRepository {

	protected HSQLDBRepository repository;

	public HSQLDBVotingRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	public PollData fromPollName(String pollName) throws DataException {
		// TODO
		return null;
	}

	public boolean pollExists(String pollName) throws DataException {
		// TODO
		return false;
	}

	public void save(PollData pollData) throws DataException {
		// TODO
	}

	public void delete(String pollName) throws DataException {
		// TODO
	}

}
