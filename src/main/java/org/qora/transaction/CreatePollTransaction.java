package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.CreatePollTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.voting.PollOptionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.voting.Poll;

import com.google.common.base.Utf8;

public class CreatePollTransaction extends Transaction {

	// Properties
	private CreatePollTransactionData createPollTransactionData;

	// Constructors

	public CreatePollTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createPollTransactionData = (CreatePollTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getCreator().getAddress()))
			return true;

		if (address.equals(this.getOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	@Override
	public PublicKeyAccount getCreator() throws DataException {
		return new PublicKeyAccount(this.repository, this.createPollTransactionData.getCreatorPublicKey());
	}

	public Account getOwner() throws DataException {
		return new Account(this.repository, this.createPollTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Are CreatePollTransactions even allowed at this point?
		// In gen1 this used NTP.getTime() but surely the transaction's timestamp should be used
		if (this.createPollTransactionData.getTimestamp() < BlockChain.getInstance().getVotingReleaseTimestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// Check owner address is valid
		if (!Crypto.isValidAddress(createPollTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		int pollNameLength = Utf8.encodedLength(createPollTransactionData.getPollName());
		if (pollNameLength < 1 || pollNameLength > Poll.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int pollDescriptionLength = Utf8.encodedLength(createPollTransactionData.getDescription());
		if (pollDescriptionLength < 1 || pollDescriptionLength > Poll.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check poll name is lowercase
		if (!createPollTransactionData.getPollName().equals(createPollTransactionData.getPollName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		// In gen1 we tested for presence of existing votes but how could there be any if poll doesn't exist?

		// Check number of options
		List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
		int pollOptionsCount = pollOptions.size();
		if (pollOptionsCount < 1 || pollOptionsCount > Poll.MAX_OPTIONS)
			return ValidationResult.INVALID_OPTIONS_COUNT;

		// Check each option
		List<String> optionNames = new ArrayList<String>();
		for (PollOptionData pollOptionData : pollOptions) {
			// Check option length
			int optionNameLength = Utf8.encodedLength(pollOptionData.getOptionName());
			if (optionNameLength < 1 || optionNameLength > Poll.MAX_NAME_SIZE)
				return ValidationResult.INVALID_OPTION_LENGTH;

			// Check option is unique. NOTE: NOT case-sensitive!
			if (optionNames.contains(pollOptionData.getOptionName())) {
				return ValidationResult.DUPLICATE_OPTION;
			}

			optionNames.add(pollOptionData.getOptionName());
		}

		// Check fee is positive
		if (createPollTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		Account creator = getCreator();

		// Check issuer has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(createPollTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the poll name isn't already taken
		if (this.repository.getVotingRepository().pollExists(createPollTransactionData.getPollName()))
			return ValidationResult.POLL_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Publish poll to allow voting
		Poll poll = new Poll(this.repository, createPollTransactionData);
		poll.publish();

		// We would save updated transaction at this point, but it hasn't been modified
	}

	@Override
	public void orphan() throws DataException {
		// Unpublish poll
		Poll poll = new Poll(this.repository, createPollTransactionData.getPollName());
		poll.unpublish();

		// We would save updated transaction at this point, but it hasn't been modified

		// Update creator's balance
		Account creator = getCreator();
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(createPollTransactionData.getFee()));

		// Update creator's reference
		creator.setLastReference(createPollTransactionData.getReference());
	}

}
