package qora.transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import data.transaction.CreatePollTransactionData;
import data.transaction.TransactionData;
import data.voting.PollOptionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.crypto.Crypto;
import qora.voting.Poll;
import repository.DataException;
import repository.Repository;

public class CreatePollTransaction extends Transaction {

	// Properties
	private CreatePollTransactionData createPollTransactionData;

	// Other useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DESCRIPTION_SIZE = 4000;
	public static final int MAX_OPTIONS = 100;

	// Constructors

	public CreatePollTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createPollTransactionData = (CreatePollTransactionData) this.transactionData;
	}

	// More information

	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getOwner());
	}

	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getCreator().getAddress()))
			return true;

		if (address.equals(this.getOwner().getAddress()))
			return true;

		return false;
	}

	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getCreator() throws DataException {
		return new PublicKeyAccount(this.repository, this.createPollTransactionData.getCreatorPublicKey());
	}

	public Account getOwner() throws DataException {
		return new Account(this.repository, this.createPollTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Are CreatePollTransactions even allowed at this point?
		// XXX In gen1 this used NTP.getTime() but surely the transaction's timestamp should be used?
		if (this.createPollTransactionData.getTimestamp() < BlockChain.VOTING_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check owner address is valid
		if (!Crypto.isValidAddress(createPollTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		if (createPollTransactionData.getPollName().length() < 1 || createPollTransactionData.getPollName().length() > CreatePollTransaction.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		if (createPollTransactionData.getDescription().length() < 1
				|| createPollTransactionData.getDescription().length() > CreatePollTransaction.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check poll name is lowercase
		if (!createPollTransactionData.getPollName().equals(createPollTransactionData.getPollName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		// Check the poll name isn't already taken
		if (this.repository.getVotingRepository().pollExists(createPollTransactionData.getPollName()))
			return ValidationResult.POLL_ALREADY_EXISTS;

		// XXX In gen1 we tested for votes but how can there be any if poll doesn't exist?

		// Check number of options
		List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
		int pollOptionsCount = pollOptions.size();
		if (pollOptionsCount < 1 || pollOptionsCount > MAX_OPTIONS)
			return ValidationResult.INVALID_OPTIONS_COUNT;

		// Check each option
		List<String> optionNames = new ArrayList<String>();
		for (PollOptionData pollOptionData : pollOptions) {
			// Check option length
			int optionNameLength = pollOptionData.getOptionName().getBytes(StandardCharsets.UTF_8).length;
			if (optionNameLength < 1 || optionNameLength > MAX_NAME_SIZE)
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
		PublicKeyAccount creator = new PublicKeyAccount(this.repository, createPollTransactionData.getCreatorPublicKey());

		if (!Arrays.equals(creator.getLastReference(), createPollTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(createPollTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Publish poll to allow voting
		Poll poll = new Poll(this.repository, createPollTransactionData);
		poll.publish();

		// Save this transaction, now with corresponding pollId
		this.repository.getTransactionRepository().save(createPollTransactionData);

		// Update creator's balance
		Account creator = new PublicKeyAccount(this.repository, createPollTransactionData.getCreatorPublicKey());
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(createPollTransactionData.getFee()));

		// Update creator's reference
		creator.setLastReference(createPollTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Unpublish poll
		Poll poll = new Poll(this.repository, createPollTransactionData.getPollName());
		poll.unpublish();

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(createPollTransactionData);

		// Update issuer's balance
		Account creator = new PublicKeyAccount(this.repository, createPollTransactionData.getCreatorPublicKey());
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(createPollTransactionData.getFee()));

		// Update issuer's reference
		creator.setLastReference(createPollTransactionData.getReference());
	}

}
