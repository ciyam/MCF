package repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import data.voting.PollData;
import data.voting.PollOptionData;
import repository.VotingRepository;
import repository.DataException;

public class HSQLDBVotingRepository implements VotingRepository {

	protected HSQLDBRepository repository;

	public HSQLDBVotingRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	public PollData fromPollName(String pollName) throws DataException {
		try {
			ResultSet resultSet = this.repository.checkedExecute("SELECT description, creator, owner, published FROM Polls WHERE poll_name = ?", pollName);
			if (resultSet == null)
				return null;

			String description = resultSet.getString(1);
			byte[] creatorPublicKey = resultSet.getBytes(2);
			String owner = resultSet.getString(3);
			long published = resultSet.getTimestamp(4).getTime();

			resultSet = this.repository.checkedExecute("SELECT option_name FROM PollOptions where poll_name = ?", pollName);
			if (resultSet == null)
				return null;

			List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String optionName = resultSet.getString(1);

				pollOptions.add(new PollOptionData(optionName));
			} while (resultSet.next());

			return new PollData(creatorPublicKey, owner, pollName, description, pollOptions, published);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch poll from repository", e);
		}
	}

	public boolean pollExists(String pollName) throws DataException {
		try {
			return this.repository.exists("Polls", "poll_name = ?", pollName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for poll in repository", e);
		}
	}

	public void save(PollData pollData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Polls");

		saveHelper.bind("poll_name", pollData.getPollName()).bind("description", pollData.getDescription()).bind("creator", pollData.getCreatorPublicKey())
				.bind("owner", pollData.getOwner()).bind("published", new Timestamp(pollData.getPublished()));

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save poll into repository", e);
		}

		// Now attempt to save poll options
		for (PollOptionData pollOptionData : pollData.getPollOptions()) {
			HSQLDBSaver optionSaveHelper = new HSQLDBSaver("PollOptions");

			optionSaveHelper.bind("poll_name", pollData.getPollName()).bind("option_name", pollOptionData.getOptionName());

			try {
				optionSaveHelper.execute(this.repository);
			} catch (SQLException e) {
				throw new DataException("Unable to save poll option into repository", e);
			}
		}
	}

	public void delete(String pollName) throws DataException {
		// NOTE: The corresponding rows in PollOptions are deleted automatically by the database thanks to "ON DELETE CASCADE" in the PollOptions' FOREIGN KEY
		// definition.
		try {
			this.repository.checkedExecute("DELETE FROM Polls WHERE poll_name = ?", pollName);
		} catch (SQLException e) {
			throw new DataException("Unable to delete poll from repository", e);
		}
	}

}
