package repository;

import data.voting.PollData;

public interface VotingRepository {

	public PollData fromPollName(String pollName) throws DataException;

	public boolean pollExists(String pollName) throws DataException;

	public void save(PollData pollData) throws DataException;

	public void delete(String pollName) throws DataException;

}
