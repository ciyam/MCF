package repository;

import data.voting.PollData;
import data.voting.VoteOnPollData;

public interface VotingRepository {

	// Polls

	public PollData fromPollName(String pollName) throws DataException;

	public boolean pollExists(String pollName) throws DataException;

	public void save(PollData pollData) throws DataException;

	public void delete(String pollName) throws DataException;

	// Votes

	public VoteOnPollData getVote(String pollName, byte[] voterPublicKey) throws DataException;

	public void save(VoteOnPollData voteOnPollData) throws DataException;

	public void delete(String pollName, byte[] voterPublicKey) throws DataException;

}
