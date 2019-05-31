package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.qora.data.transaction.CreatePollTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.voting.PollOptionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBCreatePollTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreatePollTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT owner, poll_name, description FROM CreatePollTransactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String pollName = resultSet.getString(2);
			String description = resultSet.getString(3);

			try (ResultSet optionsResultSet = this.repository
					.checkedExecute("SELECT option_name FROM CreatePollTransactionOptions WHERE signature = ? ORDER BY option_index ASC", signature)) {
				if (optionsResultSet == null)
					return null;

				List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();

				// NOTE: do-while because checkedExecute() above has already called rs.next() for us
				do {
					String optionName = optionsResultSet.getString(1);

					pollOptions.add(new PollOptionData(optionName));
				} while (optionsResultSet.next());

				return new CreatePollTransactionData(timestamp, txGroupId, reference, creatorPublicKey, owner, pollName, description, pollOptions, 
						fee, approvalStatus, height, signature);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to fetch create poll transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CreatePollTransactions");

		saveHelper.bind("signature", createPollTransactionData.getSignature()).bind("creator", createPollTransactionData.getCreatorPublicKey())
				.bind("owner", createPollTransactionData.getOwner()).bind("poll_name", createPollTransactionData.getPollName())
				.bind("description", createPollTransactionData.getDescription());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save create poll transaction into repository", e);
		}

		// Now attempt to save poll options
		List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
		for (int optionIndex = 0; optionIndex < pollOptions.size(); ++optionIndex) {
			PollOptionData pollOptionData = pollOptions.get(optionIndex);

			HSQLDBSaver optionSaveHelper = new HSQLDBSaver("CreatePollTransactionOptions");

			optionSaveHelper.bind("signature", createPollTransactionData.getSignature()).bind("option_name", pollOptionData.getOptionName())
					.bind("option_index", optionIndex);

			try {
				optionSaveHelper.execute(this.repository);
			} catch (SQLException e) {
				throw new DataException("Unable to save create poll transaction option into repository", e);
			}
		}
	}

}
