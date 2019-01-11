package org.qora.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.PrivateKeyAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.settings.Settings;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;
import org.qora.utils.NTP;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public abstract class Transaction {

	// Transaction types
	public enum TransactionType {
		GENESIS(1),
		PAYMENT(2),
		REGISTER_NAME(3),
		UPDATE_NAME(4),
		SELL_NAME(5),
		CANCEL_SELL_NAME(6),
		BUY_NAME(7),
		CREATE_POLL(8),
		VOTE_ON_POLL(9),
		ARBITRARY(10),
		ISSUE_ASSET(11),
		TRANSFER_ASSET(12),
		CREATE_ASSET_ORDER(13),
		CANCEL_ASSET_ORDER(14),
		MULTIPAYMENT(15),
		DEPLOY_AT(16),
		MESSAGE(17),
		DELEGATION(18),
		SUPERNODE(19),
		AIRDROP(20),
		AT(21),
		CREATE_GROUP(22),
		UPDATE_GROUP(23),
		ADD_GROUP_ADMIN(24),
		REMOVE_GROUP_ADMIN(25),
		GROUP_BAN(26),
		GROUP_UNBAN(27),
		GROUP_KICK(28),
		GROUP_INVITE(29),
		CANCEL_GROUP_INVITE(30),
		JOIN_GROUP(31),
		LEAVE_GROUP(32);

		public final int value;

		private final static Map<Integer, TransactionType> map = stream(TransactionType.values()).collect(toMap(type -> type.value, type -> type));

		TransactionType(int value) {
			this.value = value;
		}

		public static TransactionType valueOf(int value) {
			return map.get(value);
		}
	}

	// Validation results
	public enum ValidationResult {
		OK(1),
		INVALID_ADDRESS(2),
		NEGATIVE_AMOUNT(3),
		NEGATIVE_FEE(4),
		NO_BALANCE(5),
		INVALID_REFERENCE(6),
		INVALID_NAME_LENGTH(7),
		INVALID_VALUE_LENGTH(8),
		NAME_ALREADY_REGISTERED(9),
		NAME_DOES_NOT_EXIST(10),
		INVALID_NAME_OWNER(11),
		NAME_ALREADY_FOR_SALE(12),
		NAME_NOT_FOR_SALE(13),
		BUYER_ALREADY_OWNER(14),
		INVALID_AMOUNT(15),
		INVALID_SELLER(16),
		NAME_NOT_LOWER_CASE(17),
		INVALID_DESCRIPTION_LENGTH(18),
		INVALID_OPTIONS_COUNT(19),
		INVALID_OPTION_LENGTH(20),
		DUPLICATE_OPTION(21),
		POLL_ALREADY_EXISTS(22),
		POLL_DOES_NOT_EXIST(24),
		POLL_OPTION_DOES_NOT_EXIST(25),
		ALREADY_VOTED_FOR_THAT_OPTION(26),
		INVALID_DATA_LENGTH(27),
		INVALID_QUANTITY(28),
		ASSET_DOES_NOT_EXIST(29),
		INVALID_RETURN(30),
		HAVE_EQUALS_WANT(31),
		ORDER_DOES_NOT_EXIST(32),
		INVALID_ORDER_CREATOR(33),
		INVALID_PAYMENTS_COUNT(34),
		NEGATIVE_PRICE(35),
		INVALID_CREATION_BYTES(36),
		INVALID_TAGS_LENGTH(37),
		INVALID_AT_TYPE_LENGTH(38),
		INVALID_AT_TRANSACTION(39),
		AT_IS_FINISHED(40),
		ASSET_DOES_NOT_MATCH_AT(41),
		ASSET_ALREADY_EXISTS(43),
		MISSING_CREATOR(44),
		TIMESTAMP_TOO_OLD(45),
		TIMESTAMP_TOO_NEW(46),
		TOO_MANY_UNCONFIRMED(47),
		GROUP_ALREADY_EXISTS(48),
		GROUP_DOES_NOT_EXIST(49),
		INVALID_GROUP_OWNER(50),
		ALREADY_GROUP_MEMBER(51),
		GROUP_OWNER_CANNOT_LEAVE(52),
		NOT_GROUP_MEMBER(53),
		ALREADY_GROUP_ADMIN(54),
		NOT_GROUP_ADMIN(55),
		NOT_YET_RELEASED(1000);

		public final int value;

		private final static Map<Integer, ValidationResult> map = stream(ValidationResult.values()).collect(toMap(result -> result.value, result -> result));

		ValidationResult(int value) {
			this.value = value;
		}

		public static ValidationResult valueOf(int value) {
			return map.get(value);
		}
	}

	private static final Logger LOGGER = LogManager.getLogger(Transaction.class);

	// Properties
	protected Repository repository;
	protected TransactionData transactionData;

	// Constructors

	/**
	 * Basic constructor for use by subclasses.
	 * 
	 * @param repository
	 * @param transactionData
	 */
	protected Transaction(Repository repository, TransactionData transactionData) {
		this.repository = repository;
		this.transactionData = transactionData;
	}

	/**
	 * Returns subclass of Transaction constructed using passed transaction data.
	 * <p>
	 * Uses transaction-type in transaction data to call relevant subclass constructor.
	 * 
	 * @param repository
	 * @param transactionData
	 * @return a Transaction subclass, or null if a transaction couldn't be determined/built from passed data
	 */
	public static Transaction fromData(Repository repository, TransactionData transactionData) {
		switch (transactionData.getType()) {
			case GENESIS:
				return new GenesisTransaction(repository, transactionData);

			case PAYMENT:
				return new PaymentTransaction(repository, transactionData);

			case REGISTER_NAME:
				return new RegisterNameTransaction(repository, transactionData);

			case UPDATE_NAME:
				return new UpdateNameTransaction(repository, transactionData);

			case SELL_NAME:
				return new SellNameTransaction(repository, transactionData);

			case CANCEL_SELL_NAME:
				return new CancelSellNameTransaction(repository, transactionData);

			case BUY_NAME:
				return new BuyNameTransaction(repository, transactionData);

			case CREATE_POLL:
				return new CreatePollTransaction(repository, transactionData);

			case VOTE_ON_POLL:
				return new VoteOnPollTransaction(repository, transactionData);

			case ARBITRARY:
				return new ArbitraryTransaction(repository, transactionData);

			case ISSUE_ASSET:
				return new IssueAssetTransaction(repository, transactionData);

			case TRANSFER_ASSET:
				return new TransferAssetTransaction(repository, transactionData);

			case CREATE_ASSET_ORDER:
				return new CreateOrderTransaction(repository, transactionData);

			case CANCEL_ASSET_ORDER:
				return new CancelOrderTransaction(repository, transactionData);

			case MULTIPAYMENT:
				return new MultiPaymentTransaction(repository, transactionData);

			case DEPLOY_AT:
				return new DeployATTransaction(repository, transactionData);

			case MESSAGE:
				return new MessageTransaction(repository, transactionData);

			case AT:
				return new ATTransaction(repository, transactionData);

			case CREATE_GROUP:
				return new CreateGroupTransaction(repository, transactionData);

			case UPDATE_GROUP:
				return new UpdateGroupTransaction(repository, transactionData);

			case ADD_GROUP_ADMIN:
				return new AddGroupAdminTransaction(repository, transactionData);

			case REMOVE_GROUP_ADMIN:
				return new RemoveGroupAdminTransaction(repository, transactionData);

			case JOIN_GROUP:
				return new JoinGroupTransaction(repository, transactionData);

			case LEAVE_GROUP:
				return new LeaveGroupTransaction(repository, transactionData);

			default:
				throw new IllegalStateException("Unsupported transaction type [" + transactionData.getType().value + "] during fetch from repository");
		}
	}

	// Getters / Setters

	public TransactionData getTransactionData() {
		return this.transactionData;
	}

	// More information

	public long getDeadline() {
		// 24 hour deadline to include transaction in a block
		return this.transactionData.getTimestamp() + (24 * 60 * 60 * 1000);
	}

	public boolean hasMinimumFee() {
		return this.transactionData.getFee().compareTo(BlockChain.getInstance().getUnitFee()) >= 0;
	}

	public BigDecimal feePerByte() {
		try {
			return this.transactionData.getFee().divide(new BigDecimal(TransactionTransformer.getDataLength(this.transactionData)), MathContext.DECIMAL32);
		} catch (TransformationException e) {
			throw new IllegalStateException("Unable to get transaction byte length?");
		}
	}

	public boolean hasMinimumFeePerByte() {
		return this.feePerByte().compareTo(BlockChain.getInstance().getMinFeePerByte()) >= 0;
	}

	public BigDecimal calcRecommendedFee() {
		int dataLength;
		try {
			dataLength = TransactionTransformer.getDataLength(this.transactionData);
		} catch (TransformationException e) {
			throw new IllegalStateException("Unable to get transaction byte length?");
		}

		BigDecimal maxBytePerUnitFee = BlockChain.getInstance().getMaxBytesPerUnitFee();

		BigDecimal recommendedFee = BigDecimal.valueOf(dataLength).divide(maxBytePerUnitFee, MathContext.DECIMAL32).setScale(8);

		// security margin
		recommendedFee = recommendedFee.add(new BigDecimal("0.00000001"));

		if (recommendedFee.compareTo(BlockChain.getInstance().getUnitFee()) <= 0) {
			recommendedFee = BlockChain.getInstance().getUnitFee();
		} else {
			recommendedFee = recommendedFee.setScale(0, BigDecimal.ROUND_UP);
		}

		return recommendedFee.setScale(8);
	}

	/**
	 * Return the transaction version number that should be used, based on passed timestamp.
	 * 
	 * @param timestamp
	 * @return transaction version number, likely 1 or 3
	 */
	public static int getVersionByTimestamp(long timestamp) {
		if (timestamp < BlockChain.getInstance().getPowFixReleaseTimestamp()) {
			return 1;
		} else if (timestamp < BlockChain.getInstance().getQoraV2Timestamp()) {
			return 3;
		} else {
			return 4;
		}
	}

	/**
	 * Get block height for this transaction in the blockchain.
	 * 
	 * @return height, or 0 if not in blockchain (i.e. unconfirmed)
	 * @throws DataException
	 */
	public int getHeight() throws DataException {
		return this.repository.getTransactionRepository().getHeightFromSignature(this.transactionData.getSignature());
	}

	/**
	 * Get number of confirmations for this transaction.
	 * 
	 * @return confirmation count, or 0 if not in blockchain (i.e. unconfirmed)
	 * @throws DataException
	 */
	public int getConfirmations() throws DataException {
		int ourHeight = getHeight();
		if (ourHeight == 0)
			return 0;

		int blockChainHeight = this.repository.getBlockRepository().getBlockchainHeight();
		if (blockChainHeight == 0)
			return 0;

		return blockChainHeight - ourHeight + 1;
	}

	/**
	 * Returns a list of recipient accounts for this transaction.
	 * 
	 * @return list of recipients accounts, or empty list if none
	 * @throws DataException
	 */
	public abstract List<Account> getRecipientAccounts() throws DataException;

	/**
	 * Returns a list of involved accounts for this transaction.
	 * <p>
	 * "Involved" means sender or recipient.
	 * 
	 * @return list of involved accounts, or empty list if none
	 * @throws DataException
	 */
	public List<Account> getInvolvedAccounts() throws DataException {
		// Typically this is all the recipients plus the transaction creator/sender
		List<Account> participants = new ArrayList<Account>(getRecipientAccounts());
		participants.add(getCreator());
		return participants;
	}

	/**
	 * Returns whether passed account is an involved party in this transaction.
	 * <p>
	 * Account could be sender, or any one of the potential recipients.
	 * 
	 * @param account
	 * @return true if account is involved, false otherwise
	 * @throws DataException
	 */
	public abstract boolean isInvolved(Account account) throws DataException;

	/**
	 * Returns amount of QORA lost/gained by passed account due to this transaction.
	 * <p>
	 * Amounts "lost", e.g. sent by sender and fees, are returned as negative values.<br>
	 * Amounts "gained", e.g. QORA sent to recipient, are returned as positive values.
	 * 
	 * @param account
	 * @return Amount of QORA lost/gained by account, or BigDecimal.ZERO otherwise
	 * @throws DataException
	 */
	public abstract BigDecimal getAmount(Account account) throws DataException;

	// Navigation

	/**
	 * Return transaction's "creator" account.
	 * 
	 * @return creator
	 * @throws DataException
	 */
	protected PublicKeyAccount getCreator() throws DataException {
		if (this.transactionData.getCreatorPublicKey() == null)
			return null;

		return new PublicKeyAccount(this.repository, this.transactionData.getCreatorPublicKey());
	}

	/**
	 * Load parent's transaction data from repository via this transaction's reference.
	 * 
	 * @return Parent's TransactionData, or null if no parent found (which should not happen)
	 * @throws DataException
	 */
	public TransactionData getParent() throws DataException {
		byte[] reference = this.transactionData.getReference();
		if (reference == null)
			return null;

		return this.repository.getTransactionRepository().fromSignature(reference);
	}

	/**
	 * Load child's transaction data from repository, if any.
	 * 
	 * @return Child's TransactionData, or null if no child found
	 * @throws DataException
	 */
	public TransactionData getChild() throws DataException {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return null;

		return this.repository.getTransactionRepository().fromReference(signature);
	}

	// Processing

	public void sign(PrivateKeyAccount signer) {
		try {
			this.transactionData.setSignature(signer.sign(TransactionTransformer.toBytesForSigning(transactionData)));
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for signing", e);
		}
	}

	public boolean isSignatureValid() {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return false;

		try {
			return PublicKeyAccount.verify(this.transactionData.getCreatorPublicKey(), signature, TransactionTransformer.toBytesForSigning(transactionData));
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}
	}

	/**
	 * Returns whether transaction can be added to unconfirmed transactions.
	 * <p>
	 * NOTE: temporarily updates creator's lastReference to that from
	 * unconfirmed transactions, and hence calls <tt>repository.discardChanges()</tt>
	 * before exit.
	 * <p>
	 * This is not done normally because we don't want unconfirmed transactions affecting validity of transactions already included in a block.
	 * 
	 * @return true if transaction can be added to unconfirmed transactions, false otherwise
	 * @throws DataException
	 */
	public ValidationResult isValidUnconfirmed() throws DataException {
		// Transactions with a timestamp prior to latest block's timestamp are too old
		BlockData latestBlock = repository.getBlockRepository().getLastBlock();
		if (this.getDeadline() <= latestBlock.getTimestamp())
			return ValidationResult.TIMESTAMP_TOO_OLD;

		// Transactions with a timestamp too far into future are too new
		long maxTimestamp = NTP.getTime() + Settings.getInstance().getMaxTransactionTimestampFuture();
		if (this.transactionData.getTimestamp() > maxTimestamp)
			return ValidationResult.TIMESTAMP_TOO_NEW;

		try {
			PublicKeyAccount creator = this.getCreator();
			if (creator == null)
				return ValidationResult.MISSING_CREATOR;

			creator.setLastReference(creator.getUnconfirmedLastReference());
			ValidationResult result = this.isValid();

			// Reject if unconfirmed pile already has X transactions from same creator
			if (result == ValidationResult.OK && countUnconfirmedByCreator(creator) >= Settings.getInstance().getMaxUnconfirmedPerAccount())
				return ValidationResult.TOO_MANY_UNCONFIRMED;

			return result;
		} finally {
			repository.discardChanges();
		}
	}

	private int countUnconfirmedByCreator(PublicKeyAccount creator) throws DataException {
		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getAllUnconfirmedTransactions();

		int count = 0;
		for (TransactionData transactionData : unconfirmedTransactions) {
			Transaction transaction = Transaction.fromData(repository, transactionData);
			PublicKeyAccount otherCreator = transaction.getCreator();

			if (Arrays.equals(creator.getPublicKey(), otherCreator.getPublicKey()))
				++count;
		}

		return count;
	}

	/**
	 * Returns sorted, unconfirmed transactions, deleting invalid.
	 * <p>
	 * NOTE: temporarily updates accounts' lastReference to that from
	 * unconfirmed transactions, and hence calls <tt>repository.discardChanges()</tt>
	 * before exit.
	 * 
	 * @return sorted unconfirmed transactions
	 * @throws DataException
	 */
	public static List<TransactionData> getUnconfirmedTransactions(Repository repository) throws DataException {
		BlockData latestBlockData = repository.getBlockRepository().getLastBlock();

		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getAllUnconfirmedTransactions();
		List<TransactionData> invalidTransactions = new ArrayList<>();

		unconfirmedTransactions.sort(getDataComparator());

		for (int i = 0; i < unconfirmedTransactions.size(); ++i) {
			TransactionData transactionData = unconfirmedTransactions.get(i);

			if (!isStillValidUnconfirmed(repository, transactionData, latestBlockData.getTimestamp())) {
				invalidTransactions.add(transactionData);

				unconfirmedTransactions.remove(i);
				--i;
				continue;
			}
		}

		// Throw away temporary updates to account lastReference
		repository.discardChanges();

		// Actually delete invalid transactions from database
		for (TransactionData invalidTransactionData : invalidTransactions) {
			LOGGER.trace(String.format("Deleting invalid, unconfirmed transaction %s", Base58.encode(invalidTransactionData.getSignature())));
			repository.getTransactionRepository().delete(invalidTransactionData);
		}
		repository.saveChanges();

		return unconfirmedTransactions;
	}

	/**
	 * Returns whether transaction is still a valid unconfirmed transaction.
	 * <p>
	 * NOTE: temporarily updates creator's lastReference to that from
	 * unconfirmed transactions, and hence caller should invoke
	 * <tt>repository.discardChanges()</tt>.
	 * 
	 * @return true if transaction can be added to unconfirmed transactions, false otherwise
	 * @throws DataException
	 */
	private static boolean isStillValidUnconfirmed(Repository repository, TransactionData transactionData, long blockTimestamp) throws DataException {
		Transaction transaction = Transaction.fromData(repository, transactionData);

		// Check transaction has not expired
		if (transaction.getDeadline() <= blockTimestamp || transaction.getDeadline() < NTP.getTime())
			return false;

		// Check transaction is currently valid
		if (transaction.isValid() != Transaction.ValidationResult.OK)
			return false;

		// Good for adding to a block
		// Temporarily update sender's last reference so that subsequent transactions validations work
		// These updates should be discarded by some caller further up stack
		PublicKeyAccount creator = new PublicKeyAccount(repository, transactionData.getCreatorPublicKey());
		creator.setLastReference(transactionData.getSignature());
		return true;
	}

	/**
	 * Returns whether transaction can be added to the blockchain.
	 * <p>
	 * Checks if transaction can have {@link TransactionHandler#process()} called.
	 * <p>
	 * Transactions that have already been processed will return false.
	 * 
	 * @return true if transaction can be processed, false otherwise
	 */
	public abstract ValidationResult isValid() throws DataException;

	/**
	 * Actually process a transaction, updating the blockchain.
	 * <p>
	 * Processes transaction, updating balances, references, assets, etc. as appropriate.
	 * 
	 * @throws DataException
	 */
	public abstract void process() throws DataException;

	/**
	 * Undo transaction, updating the blockchain.
	 * <p>
	 * Undoes transaction, updating balances, references, assets, etc. as appropriate.
	 * 
	 * @throws DataException
	 */
	public abstract void orphan() throws DataException;

	// Comparison

	/** Returns comparator that sorts ATTransactions first, then by timestamp, then by signature */
	public static Comparator<Transaction> getComparator() {
		class TransactionComparator implements Comparator<Transaction> {

			private Comparator<TransactionData> transactionDataComparator;

			public TransactionComparator(Comparator<TransactionData> transactionDataComparator) {
				this.transactionDataComparator = transactionDataComparator;
			}

			// Compare by type, timestamp, then signature
			@Override
			public int compare(Transaction t1, Transaction t2) {
				TransactionData td1 = t1.getTransactionData();
				TransactionData td2 = t2.getTransactionData();

				return transactionDataComparator.compare(td1, td2);
			}

		}

		return new TransactionComparator(getDataComparator());
	}

	public static Comparator<TransactionData> getDataComparator() {
		class TransactionDataComparator implements Comparator<TransactionData> {

			// Compare by type, timestamp, then signature
			@Override
			public int compare(TransactionData td1, TransactionData td2) {
				// AT transactions come before non-AT transactions
				if (td1.getType() == TransactionType.AT && td2.getType() != TransactionType.AT)
					return -1;
				// Non-AT transactions come after AT transactions
				if (td1.getType() != TransactionType.AT && td2.getType() == TransactionType.AT)
					return 1;

				// Both transactions are either AT or non-AT so compare timestamps
				int result = Long.compare(td1.getTimestamp(), td2.getTimestamp());

				if (result == 0)
					// Same timestamp so compare signatures
					result = new BigInteger(td1.getSignature()).compareTo(new BigInteger(td2.getSignature()));

				return result;
			}

		}

		return new TransactionDataComparator();
	}

	@Override
	public int hashCode() {
		return this.transactionData.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof TransactionData))
			return false;

		return this.transactionData.equals(other);
	}

}
