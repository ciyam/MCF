package qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Utf8;

import data.transaction.TransactionData;
import data.transaction.VoteOnPollTransactionData;
import data.voting.PollData;
import data.voting.PollOptionData;
import data.voting.VoteOnPollData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.voting.Poll;
import repository.DataException;
import repository.Repository;
import repository.VotingRepository;

public class VoteOnPollTransaction extends Transaction {

	private static final Logger LOGGER = LogManager.getLogger(VoteOnPollTransaction.class);

	// Properties
	private VoteOnPollTransactionData voteOnPollTransactionData;

	// Constructors

	public VoteOnPollTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.voteOnPollTransactionData = (VoteOnPollTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() {
		return new ArrayList<Account>();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		return account.getAddress().equals(this.getCreator().getAddress());
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (account.getAddress().equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getVoter() throws DataException {
		return new PublicKeyAccount(this.repository, voteOnPollTransactionData.getVoterPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Are VoteOnPollTransactions even allowed at this point?
		// XXX In gen1 this used NTP.getTime() but surely the transaction's timestamp should be used?
		if (this.voteOnPollTransactionData.getTimestamp() < BlockChain.getVotingReleaseTimestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// Check name size bounds
		int pollNameLength = Utf8.encodedLength(voteOnPollTransactionData.getPollName());
		if (pollNameLength < 1 || pollNameLength > Poll.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check poll name is lowercase
		if (!voteOnPollTransactionData.getPollName().equals(voteOnPollTransactionData.getPollName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		VotingRepository votingRepository = this.repository.getVotingRepository();

		// Check poll exists
		PollData pollData = votingRepository.fromPollName(voteOnPollTransactionData.getPollName());
		if (pollData == null)
			return ValidationResult.POLL_DOES_NOT_EXIST;

		// Check poll option index is within bounds
		List<PollOptionData> pollOptions = pollData.getPollOptions();
		int optionIndex = voteOnPollTransactionData.getOptionIndex();

		if (optionIndex < 0 || optionIndex > pollOptions.size() - 1)
			return ValidationResult.POLL_OPTION_DOES_NOT_EXIST;

		// Check if vote already exists
		VoteOnPollData voteOnPollData = votingRepository.getVote(voteOnPollTransactionData.getPollName(), voteOnPollTransactionData.getVoterPublicKey());
		if (voteOnPollData != null && voteOnPollData.getOptionIndex() == optionIndex)
			return ValidationResult.ALREADY_VOTED_FOR_THAT_OPTION;

		// Check fee is positive
		if (voteOnPollTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		Account voter = getVoter();

		if (!Arrays.equals(voter.getLastReference(), voteOnPollTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check voter has enough funds
		if (voter.getConfirmedBalance(Asset.QORA).compareTo(voteOnPollTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update voter's balance
		Account voter = getVoter();
		voter.setConfirmedBalance(Asset.QORA, voter.getConfirmedBalance(Asset.QORA).subtract(voteOnPollTransactionData.getFee()));

		// Update vote's reference
		voter.setLastReference(voteOnPollTransactionData.getSignature());

		VotingRepository votingRepository = this.repository.getVotingRepository();

		// Check for previous vote so we can save option in case of orphaning
		VoteOnPollData previousVoteOnPollData = votingRepository.getVote(voteOnPollTransactionData.getPollName(),
				voteOnPollTransactionData.getVoterPublicKey());
		if (previousVoteOnPollData != null) {
			voteOnPollTransactionData.setPreviousOptionIndex(previousVoteOnPollData.getOptionIndex());
			LOGGER.trace("Previous vote by " + voter.getAddress() + " on poll \"" + voteOnPollTransactionData.getPollName() + "\" was option index "
					+ previousVoteOnPollData.getOptionIndex());
		}

		// Save this transaction, now with possible previous vote
		this.repository.getTransactionRepository().save(voteOnPollTransactionData);

		// Apply vote to poll
		LOGGER.trace("Vote by " + voter.getAddress() + " on poll \"" + voteOnPollTransactionData.getPollName() + "\" with option index "
				+ voteOnPollTransactionData.getOptionIndex());
		VoteOnPollData newVoteOnPollData = new VoteOnPollData(voteOnPollTransactionData.getPollName(), voteOnPollTransactionData.getVoterPublicKey(),
				voteOnPollTransactionData.getOptionIndex());
		votingRepository.save(newVoteOnPollData);
	}

	@Override
	public void orphan() throws DataException {
		// Update voter's balance
		Account voter = getVoter();
		voter.setConfirmedBalance(Asset.QORA, voter.getConfirmedBalance(Asset.QORA).add(voteOnPollTransactionData.getFee()));

		// Update voter's reference
		voter.setLastReference(voteOnPollTransactionData.getReference());

		// Does this transaction have previous vote info?
		VotingRepository votingRepository = this.repository.getVotingRepository();
		Integer previousOptionIndex = voteOnPollTransactionData.getPreviousOptionIndex();
		if (previousOptionIndex != null) {
			// Reinstate previous vote
			LOGGER.trace("Reinstating previous vote by " + voter.getAddress() + " on poll \"" + voteOnPollTransactionData.getPollName()
					+ "\" with option index " + previousOptionIndex);
			VoteOnPollData previousVoteOnPollData = new VoteOnPollData(voteOnPollTransactionData.getPollName(), voteOnPollTransactionData.getVoterPublicKey(),
					previousOptionIndex);
			votingRepository.save(previousVoteOnPollData);
		} else {
			// Delete vote
			LOGGER.trace("Deleting vote by " + voter.getAddress() + " on poll \"" + voteOnPollTransactionData.getPollName() + "\" with option index "
					+ voteOnPollTransactionData.getOptionIndex());
			votingRepository.delete(voteOnPollTransactionData.getPollName(), voteOnPollTransactionData.getVoterPublicKey());
		}

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(voteOnPollTransactionData);
	}

}
