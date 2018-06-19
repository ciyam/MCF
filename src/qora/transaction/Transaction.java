package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.block.BlockChain;
import repository.DataException;
import repository.Repository;
import settings.Settings;
import transform.TransformationException;
import transform.Transformer;
import transform.transaction.TransactionTransformer;

public abstract class Transaction {

	// Transaction types
	public enum TransactionType {
		GENESIS(1), PAYMENT(2), REGISTER_NAME(3), UPDATE_NAME(4), SELL_NAME(5), CANCEL_SELL_NAME(6), BUY_NAME(7), CREATE_POLL(8), VOTE_ON_POLL(9), ARBITRARY(
				10), ISSUE_ASSET(11), TRANSFER_ASSET(12), CREATE_ASSET_ORDER(13), CANCEL_ASSET_ORDER(14), MULTIPAYMENT(15), DEPLOY_AT(16), MESSAGE(17);

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
		OK(1), INVALID_ADDRESS(2), NEGATIVE_AMOUNT(3), NEGATIVE_FEE(4), NO_BALANCE(5), INVALID_REFERENCE(6), INVALID_NAME_LENGTH(7), INVALID_AMOUNT(
				15), NAME_NOT_LOWER_CASE(17), INVALID_DESCRIPTION_LENGTH(18), INVALID_OPTIONS_COUNT(19), INVALID_OPTION_LENGTH(20), DUPLICATE_OPTION(
						21), POLL_ALREADY_EXISTS(22), INVALID_DATA_LENGTH(27), INVALID_QUANTITY(28), ASSET_DOES_NOT_EXIST(29), INVALID_RETURN(
								30), HAVE_EQUALS_WANT(31), ORDER_DOES_NOT_EXIST(32), INVALID_ORDER_CREATOR(
										33), INVALID_PAYMENTS_COUNT(34), NEGATIVE_PRICE(35), ASSET_ALREADY_EXISTS(43), NOT_YET_RELEASED(1000);

		public final int value;

		private final static Map<Integer, ValidationResult> map = stream(ValidationResult.values()).collect(toMap(result -> result.value, result -> result));

		ValidationResult(int value) {
			this.value = value;
		}

		public static ValidationResult valueOf(int value) {
			return map.get(value);
		}
	}

	// Minimum fee
	public static final BigDecimal MINIMUM_FEE = BigDecimal.ONE;

	// Cached info to make transaction processing faster
	protected static final BigDecimal maxBytePerFee = BigDecimal.valueOf(Settings.getInstance().getMaxBytePerFee());
	protected static final BigDecimal minFeePerByte = BigDecimal.ONE.divide(maxBytePerFee, MathContext.DECIMAL32);

	// Properties
	protected Repository repository;
	protected TransactionData transactionData;

	// Constructors

	protected Transaction(Repository repository, TransactionData transactionData) {
		this.repository = repository;
		this.transactionData = transactionData;
	}

	public static Transaction fromData(Repository repository, TransactionData transactionData) {
		switch (transactionData.getType()) {
			case GENESIS:
				return new GenesisTransaction(repository, transactionData);

			case PAYMENT:
				return new PaymentTransaction(repository, transactionData);

			case CREATE_POLL:
				return new CreatePollTransaction(repository, transactionData);

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

			case MESSAGE:
				return new MessageTransaction(repository, transactionData);

			default:
				return null;
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
		return this.transactionData.getFee().compareTo(MINIMUM_FEE) >= 0;
	}

	public BigDecimal feePerByte() {
		try {
			return this.transactionData.getFee().divide(new BigDecimal(TransactionTransformer.getDataLength(this.transactionData)), MathContext.DECIMAL32);
		} catch (TransformationException e) {
			throw new IllegalStateException("Unable to get transaction byte length?");
		}
	}

	public boolean hasMinimumFeePerByte() {
		return this.feePerByte().compareTo(minFeePerByte) >= 0;
	}

	public BigDecimal calcRecommendedFee() {
		try {
			BigDecimal recommendedFee = BigDecimal.valueOf(TransactionTransformer.getDataLength(this.transactionData))
					.divide(maxBytePerFee, MathContext.DECIMAL32).setScale(8);

			// security margin
			recommendedFee = recommendedFee.add(new BigDecimal("0.0000001"));

			if (recommendedFee.compareTo(MINIMUM_FEE) <= 0) {
				recommendedFee = MINIMUM_FEE;
			} else {
				recommendedFee = recommendedFee.setScale(0, BigDecimal.ROUND_UP);
			}

			return recommendedFee.setScale(8);
		} catch (TransformationException e) {
			throw new IllegalStateException("Unable to get transaction byte length?");
		}
	}

	/**
	 * Return the transaction version number that should be used, based on passed timestamp.
	 * 
	 * @param timestamp
	 * @return transaction version number, likely 1 or 3
	 */
	public static int getVersionByTimestamp(long timestamp) {
		if (timestamp < BlockChain.POWFIX_RELEASE_TIMESTAMP) {
			return 1;
		} else {
			return 3;
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
	protected Account getCreator() throws DataException {
		return new PublicKeyAccount(this.repository, this.transactionData.getCreatorPublicKey());
	}

	/**
	 * Load encapsulating block's data from repository, if any
	 * 
	 * @return BlockData, or null if transaction is not in a Block
	 * @throws DataException
	 */
	public BlockData getBlock() throws DataException {
		return this.repository.getTransactionRepository().getBlockDataFromSignature(this.transactionData.getSignature());
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

	/**
	 * Serialize transaction as byte[], stripping off trailing signature.
	 * <p>
	 * Used by signature-related methods such as {@link TransactionHandler#calcSignature(PrivateKeyAccount)} and {@link TransactionHandler#isSignatureValid()}
	 * 
	 * @return byte[]
	 */
	private byte[] toBytesLessSignature() {
		try {
			byte[] bytes = TransactionTransformer.toBytes(this.transactionData);

			if (this.transactionData.getSignature() == null)
				return bytes;

			return Arrays.copyOf(bytes, bytes.length - Transformer.SIGNATURE_LENGTH);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to signature-less byte array", e);
		}
	}

	// Processing

	public void calcSignature(PrivateKeyAccount signer) {
		this.transactionData.setSignature(signer.sign(this.toBytesLessSignature()));
	}

	public boolean isSignatureValid() {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return false;

		return PublicKeyAccount.verify(this.transactionData.getCreatorPublicKey(), signature, this.toBytesLessSignature());
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

}
