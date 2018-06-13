package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.block.BlockChain;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
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
		OK(1), INVALID_ADDRESS(2), NEGATIVE_AMOUNT(3), NEGATIVE_FEE(4), NO_BALANCE(5), INVALID_REFERENCE(6), INVALID_NAME_LENGTH(7), INVALID_DESCRIPTION_LENGTH(
				18), INVALID_DATA_LENGTH(27), INVALID_QUANTITY(28), ASSET_DOES_NOT_EXIST(29), ASSET_ALREADY_EXISTS(43), NOT_YET_RELEASED(1000);

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

			case ISSUE_ASSET:
				return new IssueAssetTransaction(repository, transactionData);

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

	public static int getVersionByTimestamp(long timestamp) {
		if (timestamp < Block.POWFIX_RELEASE_TIMESTAMP) {
			return 1;
		} else {
			return 3;
		}
	}

	/**
	 * Get block height for this transaction in the blockchain.
	 * 
	 * @return height, or 0 if not in blockchain (i.e. unconfirmed)
	 */
	public int getHeight() {
		return this.repository.getTransactionRepository().getHeight(this.transactionData);
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

	// Navigation

	/**
	 * Load encapsulating Block from DB, if any
	 * 
	 * @return Block, or null if transaction is not in a Block
	 */
	public BlockData getBlock() {
		return this.repository.getTransactionRepository().toBlock(this.transactionData);
	}

	/**
	 * Load parent Transaction from DB via this transaction's reference.
	 * 
	 * @return Transaction, or null if no parent found (which should not happen)
	 * @throws DataException
	 */
	public TransactionData getParent() throws DataException {
		byte[] reference = this.transactionData.getReference();
		if (reference == null)
			return null;

		return this.repository.getTransactionRepository().fromSignature(reference);
	}

	/**
	 * Load child Transaction from DB, if any.
	 * 
	 * @return Transaction, or null if no child found
	 * @throws DataException
	 */
	public TransactionData getChild() throws DataException {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return null;

		return this.repository.getTransactionRepository().fromSignature(signature);
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
			return Arrays.copyOf(bytes, bytes.length - Transformer.SIGNATURE_LENGTH);
		} catch (TransformationException e) {
			// XXX this isn't good
			return null;
		}
	}

	// Processing

	public byte[] calcSignature(PrivateKeyAccount signer) {
		return signer.sign(this.toBytesLessSignature());
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
