package org.qora.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.PrivateKeyAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.controller.Controller;
import org.qora.data.block.BlockData;
import org.qora.data.group.GroupApprovalData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;
import org.qora.settings.Settings;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class Transaction {

	// Transaction types
	public enum TransactionType {
		// NOTE: must be contiguous or reflection fails
		GENESIS(1, false),
		PAYMENT(2, false),
		REGISTER_NAME(3, true),
		UPDATE_NAME(4, true),
		SELL_NAME(5, false),
		CANCEL_SELL_NAME(6, false),
		BUY_NAME(7, false),
		CREATE_POLL(8, true),
		VOTE_ON_POLL(9, false),
		ARBITRARY(10, true),
		ISSUE_ASSET(11, true),
		TRANSFER_ASSET(12, false),
		CREATE_ASSET_ORDER(13, false),
		CANCEL_ASSET_ORDER(14, false),
		MULTI_PAYMENT(15, false),
		DEPLOY_AT(16, true),
		MESSAGE(17, true),
		DELEGATION(18, false),
		SUPERNODE(19, false),
		AIRDROP(20, false),
		AT(21, false),
		CREATE_GROUP(22, true),
		UPDATE_GROUP(23, true),
		ADD_GROUP_ADMIN(24, false),
		REMOVE_GROUP_ADMIN(25, false),
		GROUP_BAN(26, false),
		CANCEL_GROUP_BAN(27, false),
		GROUP_KICK(28, false),
		GROUP_INVITE(29, false),
		CANCEL_GROUP_INVITE(30, false),
		JOIN_GROUP(31, false),
		LEAVE_GROUP(32, false),
		GROUP_APPROVAL(33, false),
		SET_GROUP(34, false),
		UPDATE_ASSET(35, true),
		ACCOUNT_FLAGS(36, false),
		ENABLE_FORGING(37, false),
		PROXY_FORGING(38, false);

		public final int value;
		public final boolean needsApproval;
		public final String valueString;
		public final String className;
		public final Class<?> clazz;
		public final Constructor<?> constructor;

		private final static Map<Integer, TransactionType> map = stream(TransactionType.values()).collect(toMap(type -> type.value, type -> type));

		TransactionType(int value, boolean needsApproval) {
			this.value = value;
			this.needsApproval = needsApproval;
			this.valueString = String.valueOf(value);

			String[] classNameParts = this.name().toLowerCase().split("_");

			for (int i = 0; i < classNameParts.length; ++i)
				classNameParts[i] = classNameParts[i].substring(0, 1).toUpperCase().concat(classNameParts[i].substring(1));

			this.className = String.join("", classNameParts);

			Class<?> clazz = null;
			Constructor<?> constructor = null;

			try {
				clazz = Class.forName(String.join("", Transaction.class.getPackage().getName(), ".", this.className, "Transaction"));

				try {
					constructor = clazz.getConstructor(Repository.class, TransactionData.class);
				} catch (NoSuchMethodException | SecurityException e) {
					LOGGER.debug(String.format("Transaction subclass constructor not found for transaction type \"%s\"", this.name()));
				}
			} catch (ClassNotFoundException e) {
				LOGGER.debug(String.format("Transaction subclass not found for transaction type \"%s\"", this.name()));
			}

			this.clazz = clazz;
			this.constructor = constructor;
		}

		public static TransactionType valueOf(int value) {
			return map.get(value);
		}
	}

	// Group-approval status
	public enum ApprovalStatus {
		NOT_REQUIRED(0),
		PENDING(1),
		APPROVED(2),
		REJECTED(3),
		EXPIRED(4),
		INVALID(5);

		public final int value;

		private final static Map<Integer, ApprovalStatus> map = stream(ApprovalStatus.values()).collect(toMap(result -> result.value, result -> result));

		ApprovalStatus(int value) {
			this.value = value;
		}

		public static ApprovalStatus valueOf(int value) {
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
		INSUFFICIENT_FEE(40),
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
		INVALID_LIFETIME(56),
		INVITE_UNKNOWN(57),
		BAN_EXISTS(58),
		BAN_UNKNOWN(59),
		BANNED_FROM_GROUP(60),
		JOIN_REQUEST_EXISTS(61),
		INVALID_GROUP_APPROVAL_THRESHOLD(62),
		GROUP_ID_MISMATCH(63),
		INVALID_GROUP_ID(64),
		TRANSACTION_UNKNOWN(65),
		TRANSACTION_ALREADY_CONFIRMED(66),
		INVALID_TX_GROUP_ID(67),
		TX_GROUP_ID_MISMATCH(68),
		MULTIPLE_NAMES_FORBIDDEN(69),
		INVALID_ASSET_OWNER(70),
		AT_IS_FINISHED(71),
		NO_FLAG_PERMISSION(72),
		NO_FORGING_PERMISSION(73),
		FORGING_ALREADY_ENABLED(74),
		FORGE_MORE_BLOCKS(75),
		FORGING_ENABLE_LIMIT(76),
		INVALID_FORGE_SHARE(77),
		PUBLIC_KEY_UNKNOWN(78),
		INVALID_PUBLIC_KEY(79),
		AT_UNKNOWN(80),
		AT_ALREADY_EXISTS(81),
		GROUP_APPROVAL_NOT_REQUIRED(82),
		GROUP_APPROVAL_DECIDED(83),
		MAXIMUM_PROXY_RELATIONSHIPS(84),
		TRANSACTION_ALREADY_EXISTS(85),
		NO_BLOCKCHAIN_LOCK(86),
		ORDER_ALREADY_CLOSED(87),
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
		TransactionType type = transactionData.getType();

		try {
			Constructor<?> constructor = type.constructor;

			if (constructor == null)
				throw new IllegalStateException("Unsupported transaction type [" + type.value + "] during fetch from repository");

			return (Transaction) constructor.newInstance(repository, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
			throw new IllegalStateException("Internal error with transaction type [" + type.value + "] during fetch from repository");
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
	 * NOTE: temporarily updates accounts' lastReference to check validity.<br>
	 * To do this, blockchain lock is obtained and pending repository changes are discarded.
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
		long maxTimestamp = System.currentTimeMillis() + Settings.getInstance().getMaxTransactionTimestampFuture();
		if (this.transactionData.getTimestamp() > maxTimestamp)
			return ValidationResult.TIMESTAMP_TOO_NEW;

		// Check fee is sufficient
		if (!hasMinimumFee() || !hasMinimumFeePerByte())
			return ValidationResult.INSUFFICIENT_FEE;

		/*
		 * We have to grab the blockchain lock because we're updating
		 * when we fake the creator's last reference,
		 * even though we throw away the update when we discard changes.
		 */
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		blockchainLock.lock();
		try {
			// Clear repository's "in transaction" state so we don't cause a repository deadlock
			repository.discardChanges();

			try {
				PublicKeyAccount creator = this.getCreator();
				if (creator == null)
					return ValidationResult.MISSING_CREATOR;

				// Reject if unconfirmed pile already has X transactions from same creator
				if (countUnconfirmedByCreator(creator) >= Settings.getInstance().getMaxUnconfirmedPerAccount())
					return ValidationResult.TOO_MANY_UNCONFIRMED;

				// Check transaction's txGroupId
				if (!this.isValidTxGroupId())
					return ValidationResult.INVALID_TX_GROUP_ID;

				byte[] unconfirmedLastReference = creator.getUnconfirmedLastReference();
				if (unconfirmedLastReference != null)
					creator.setLastReference(unconfirmedLastReference);

				// Check transaction is valid
				ValidationResult result = this.isValid();
				if (result != ValidationResult.OK)
					return result;

				// Check transaction references
				if (!this.hasValidReference())
					return ValidationResult.INVALID_REFERENCE;

				// Check transaction is processable
				result = this.isProcessable();

				return result;
			} finally {
				repository.discardChanges();
			}
		} finally {
			// In separate finally block just in case rollback throws
			blockchainLock.unlock();
		}
	}

	private boolean isValidTxGroupId() throws DataException {
		int txGroupId = this.transactionData.getTxGroupId();

		// If transaction type doesn't need approval then we insist on NO_GROUP
		if (!this.transactionData.getType().needsApproval)
			return txGroupId == Group.NO_GROUP;

		// Handling NO_GROUP
		if (txGroupId == Group.NO_GROUP)
			// true if NO_GROUP txGroupId is allowed for approval-needing tx types
			return !BlockChain.getInstance().getRequireGroupForApproval();

		// Group even exist?
		if (!this.repository.getGroupRepository().groupExists(txGroupId))
			return false;

		GroupRepository groupRepository = this.repository.getGroupRepository();

		// Is transaction's creator is group member?
		PublicKeyAccount creator = this.getCreator();
		if (groupRepository.memberExists(txGroupId, creator.getAddress()))
			return true;

		return false;
	}

	private int countUnconfirmedByCreator(PublicKeyAccount creator) throws DataException {
		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getUnconfirmedTransactions();

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
	 * Returns sorted, unconfirmed transactions, excluding invalid.
	 * <p>
	 * NOTE: temporarily updates accounts' lastReference to check validity.<br>
	 * To do this, blockchain lock is obtained and pending repository changes are discarded.
	 * 
	 * @return sorted, unconfirmed transactions
	 * @throws DataException
	 */
	public static List<TransactionData> getUnconfirmedTransactions(Repository repository) throws DataException {
		BlockData latestBlockData = repository.getBlockRepository().getLastBlock();

		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getUnconfirmedTransactions();

		unconfirmedTransactions.sort(getDataComparator());

		/*
		 * We have to grab the blockchain lock because we're updating
		 * when we fake the creator's last reference,
		 * even though we throw away the update when we rollback the
		 * savepoint.
		 */
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		blockchainLock.lock();
		try {
			// Clear repository's "in transaction" state so we don't cause a repository deadlock
			repository.discardChanges();

			try {
				for (int i = 0; i < unconfirmedTransactions.size(); ++i) {
					TransactionData transactionData = unconfirmedTransactions.get(i);

					if (!isStillValidUnconfirmed(repository, transactionData, latestBlockData.getTimestamp())) {
						unconfirmedTransactions.remove(i);
						--i;
						continue;
					}
				}
			} finally {
				// Throw away temporary updates to account lastReference
				repository.discardChanges();
			}
		} finally {
			// In separate finally block just in case rollback throws
			blockchainLock.unlock();
		}

		return unconfirmedTransactions;
	}

	/**
	 * Returns invalid, unconfirmed transactions.
	 * <p>
	 * NOTE: temporarily updates accounts' lastReference to check validity.<br>
	 * To do this, blockchain lock is obtained and pending repository changes are discarded.
	 * 
	 * @return sorted, invalid, unconfirmed transactions
	 * @throws DataException
	 */
	public static List<TransactionData> getInvalidTransactions(Repository repository) throws DataException {
		BlockData latestBlockData = repository.getBlockRepository().getLastBlock();

		List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getUnconfirmedTransactions();
		List<TransactionData> invalidTransactions = new ArrayList<>();

		unconfirmedTransactions.sort(getDataComparator());

		/*
		 * We have to grab the blockchain lock because we're updating
		 * when we fake the creator's last reference,
		 * even though we throw away the update when we rollback the
		 * savepoint.
		 */
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		blockchainLock.lock();
		try {
			// Clear repository's "in transaction" state so we don't cause a repository deadlock
			repository.discardChanges();

			try {
				for (int i = 0; i < unconfirmedTransactions.size(); ++i) {
					TransactionData transactionData = unconfirmedTransactions.get(i);

					if (!isStillValidUnconfirmed(repository, transactionData, latestBlockData.getTimestamp())) {
						invalidTransactions.add(transactionData);

						unconfirmedTransactions.remove(i);
						--i;
						continue;
					}
				}
			} finally {
				// Throw away temporary updates to account lastReference
				repository.discardChanges();
			}
		} finally {
			// In separate finally block just in case rollback throws
			blockchainLock.unlock();
		}

		return invalidTransactions;
	}

	/**
	 * Returns whether transaction is still a valid unconfirmed transaction.
	 * <p>
	 * NOTE: temporarily updates creator's lastReference to that from
	 * unconfirmed transactions, and hence caller should use a repository
	 * savepoint or invoke <tt>repository.discardChanges()</tt>.
	 * <p>
	 * Caller should also hold the blockchain lock as we're 'updating'
	 * when we fake the transaction creator's last reference, even if
	 * it discarded at rollback.
	 * 
	 * @return true if transaction can be added to unconfirmed transactions, false otherwise
	 * @throws DataException
	 */
	private static boolean isStillValidUnconfirmed(Repository repository, TransactionData transactionData, long blockTimestamp) throws DataException {
		Transaction transaction = Transaction.fromData(repository, transactionData);

		// Check transaction has not expired
		if (transaction.getDeadline() <= blockTimestamp || transaction.getDeadline() < System.currentTimeMillis())
			return false;

		// Is transaction is past max approval period?
		if (transaction.needsGroupApproval()) {
			int txGroupId = transactionData.getTxGroupId();
			GroupData groupData = repository.getGroupRepository().fromGroupId(txGroupId);

			int creationBlockHeight = repository.getBlockRepository().getHeightFromTimestamp(transactionData.getTimestamp());
			int currentBlockHeight = repository.getBlockRepository().getBlockchainHeight();
			if (currentBlockHeight > creationBlockHeight + groupData.getMaximumBlockDelay())
				return false;
		}

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
	 * Returns whether transaction needs to go through group-admin approval.
	 * <p>
	 * This test is more than simply "does this transaction type need approval?"
	 * because group admins bypass approval for transactions attached to their group.
	 * 
	 * @throws DataException
	 */
	public boolean needsGroupApproval() throws DataException {
		// Does this transaction type bypass approval?
		if (!this.transactionData.getType().needsApproval)
			return false;

		// Is group-approval even in effect yet?
		if (this.transactionData.getTimestamp() < BlockChain.getInstance().getGroupApprovalTimestamp())
			return false;

		int txGroupId = this.transactionData.getTxGroupId();

		if (txGroupId == Group.NO_GROUP)
			return false;

		GroupRepository groupRepository = this.repository.getGroupRepository();

		if (!groupRepository.groupExists(txGroupId))
			// Group no longer exists? Possibly due to blockchain orphaning undoing group creation?
			return true; // stops tx being included in block but it will eventually expire

		// If transaction's creator is group admin (of group with ID txGroupId) then auto-approve
		PublicKeyAccount creator = this.getCreator();
		if (groupRepository.adminExists(txGroupId, creator.getAddress()))
			return false;

		return true;
	}

	public void setInitialApprovalStatus() throws DataException {
		if (this.needsGroupApproval()) {
			transactionData.setApprovalStatus(ApprovalStatus.PENDING);
		} else {
			transactionData.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);
		}
	}

	public Boolean getApprovalDecision() throws DataException {
		// Grab latest decisions from repository
		GroupApprovalData groupApprovalData = this.repository.getTransactionRepository().getApprovalData(this.transactionData.getSignature());
		if (groupApprovalData == null)
			return null;

		// We need group info
		int txGroupId = this.transactionData.getTxGroupId();
		GroupData groupData = repository.getGroupRepository().fromGroupId(txGroupId);
		ApprovalThreshold approvalThreshold = groupData.getApprovalThreshold();

		// Fetch total number of admins in group
		int totalAdmins = repository.getGroupRepository().countGroupAdmins(txGroupId);

		// Are there enough approvals?
		if (approvalThreshold.meetsTheshold(groupApprovalData.approvingAdmins.size(), totalAdmins))
			return true;

		// Are there enough rejections?
		if (approvalThreshold.meetsTheshold(groupApprovalData.rejectingAdmins.size(), totalAdmins))
			return false;

		// No definitive decision yet
		return null;
	}

	/**
	 * Import into our repository as a new, unconfirmed transaction.
	 * <p>
	 * Calls <tt>repository.saveChanges()</tt>
	 * 
	 * @throws DataException
	 */
	public ValidationResult importAsUnconfirmed() throws DataException {
		// Attempt to acquire blockchain lock
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (!blockchainLock.tryLock())
			return ValidationResult.NO_BLOCKCHAIN_LOCK;

		try {
			// Check transaction doesn't already exist
			if (repository.getTransactionRepository().exists(transactionData.getSignature()))
				return ValidationResult.TRANSACTION_ALREADY_EXISTS;

			// Fix up approval status
			this.setInitialApprovalStatus();

			ValidationResult validationResult = this.isValidUnconfirmed();
			if (validationResult != ValidationResult.OK)
				return validationResult;

			repository.getTransactionRepository().save(transactionData);
			repository.getTransactionRepository().unconfirmTransaction(transactionData);
			repository.saveChanges();

			return ValidationResult.OK;
		} finally {
			blockchainLock.unlock();
		}
	}

	/**
	 * Returns whether transaction can be added to the blockchain.
	 * <p>
	 * Checks if transaction can have {@link TransactionHandler#process()} called.
	 * <p>
	 * Transactions that have already been processed will return false.
	 * 
	 * @return true if transaction can be processed, false otherwise
	 * @throws DataException
	 */
	public abstract ValidationResult isValid() throws DataException;

	/**
	 * Returns whether transaction's reference is valid.
	 * 
	 * @throws DataException
	 */
	public boolean hasValidReference() throws DataException {
		Account creator = getCreator();

		return Arrays.equals(transactionData.getReference(), creator.getLastReference());
	}

	/**
	 * Returns whether transaction can be processed.
	 * <p>
	 * With group-approval, even if a transaction had valid values
	 * when submitted, by the time it is approved dependency might
	 * have changed.
	 * <p>
	 * For example, with UPDATE_ASSET, the asset owner might have
	 * changed between submission and approval.
	 * 
	 * @throws DataException
	 */
	public ValidationResult isProcessable() throws DataException {
		return ValidationResult.OK;
	};

	/**
	 * Actually process a transaction, updating the blockchain.
	 * <p>
	 * Processes transaction, updating balances, references, assets, etc. as appropriate.
	 * 
	 * @throws DataException
	 */
	public abstract void process() throws DataException;

	/**
	 * Update last references, subtract transaction fees, etc.
	 * 
	 * @throws DataException
	 */
	public void processReferencesAndFees() throws DataException {
		Account creator = getCreator();

		// Update transaction creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(transactionData.getFee()));

		// Update transaction creator's reference
		creator.setLastReference(transactionData.getSignature());
	}

	/**
	 * Undo transaction, updating the blockchain.
	 * <p>
	 * Undoes transaction, updating balances, references, assets, etc. as appropriate.
	 * 
	 * @throws DataException
	 */
	public abstract void orphan() throws DataException;

	/**
	 * Update last references, subtract transaction fees, etc.
	 * 
	 * @throws DataException
	 */
	public void orphanReferencesAndFees() throws DataException {
		Account creator = getCreator();

		// Update transaction creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(transactionData.getFee()));

		// Update transaction creator's reference
		creator.setLastReference(transactionData.getReference());
	}


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
