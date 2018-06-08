package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import org.json.simple.JSONObject;

import data.transaction.Transaction;
import database.DB;
import database.NoDataFoundException;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.block.BlockChain;
import qora.block.BlockTransaction;
import repository.Repository;
import repository.RepositoryManager;
import settings.Settings;
import transform.TransformationException;
import transform.Transformer;
import transform.transaction.TransactionTransformer;
import utils.Base58;

public abstract class TransactionHandler {

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

	private Transaction transaction;
	
	// Constructors

	public TransactionHandler(Transaction transaction) {
		this.transaction = transaction;
	}

	// More information
	
	

	public long getDeadline() {
		// 24 hour deadline to include transaction in a block
		return this.transaction.getTimestamp() + (24 * 60 * 60 * 1000);
	}

	public boolean hasMinimumFee() {
		return this.transaction.getFee().compareTo(MINIMUM_FEE) >= 0;
	}

	public BigDecimal feePerByte() {
		try {
			return this.transaction.getFee().divide(new BigDecimal(TransactionTransformer.getDataLength(this.transaction)), MathContext.DECIMAL32);
		} catch (TransformationException e) {
			throw new IllegalStateException("Unable to get transaction byte length?");
		}
	}

	public boolean hasMinimumFeePerByte() {
		return this.feePerByte().compareTo(minFeePerByte) >= 0;
	}

	public BigDecimal calcRecommendedFee() {
		try {
			BigDecimal recommendedFee = BigDecimal.valueOf(TransactionTransformer.getDataLength(this.transaction)).divide(maxBytePerFee, MathContext.DECIMAL32).setScale(8);
	
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
		return RepositoryManager.getTransactionRepository().getHeight(this.transaction);
	}

	/**
	 * Get number of confirmations for this transaction.
	 * 
	 * @return confirmation count, or 0 if not in blockchain (i.e. unconfirmed)
	 */
	public int getConfirmations() {
		int ourHeight = this.getHeight();
		if (ourHeight == 0)
			return 0;

		int blockChainHeight = BlockChain.getHeight();
		return blockChainHeight - ourHeight + 1;
	}

	// Navigation

	/**
	 * Load encapsulating Block from DB, if any
	 * 
	 * @return Block, or null if transaction is not in a Block
	 */
	public Block getBlock() {
		return RepositoryManager.getTransactionRepository().toBlock(this.transaction);
	}

	/**
	 * Load parent Transaction from DB via this transaction's reference.
	 * 
	 * @return Transaction, or null if no parent found (which should not happen)
	 */
	public Transaction getParent() {
		byte[] reference = this.transaction.getReference();
		if (reference == null)
			return null;

		return RepositoryManager.getTransactionRepository().fromSignature(reference);
	}

	/**
	 * Load child Transaction from DB, if any.
	 * 
	 * @return Transaction, or null if no child found
	 */
	public Transaction getChild() {
		byte[] signature = this.transaction.getSignature();
		if (signature == null)
			return null;

		return RepositoryManager.getTransactionRepository().fromSignature(signature);
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
			byte[] bytes = TransactionTransformer.toBytes(this.transaction);
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
		byte[] signature = this.transaction.getSignature();
		if (signature == null)
			return false;

		// XXX: return this.transaction.getCreator().verify(signature, this.toBytesLessSignature());
		return false;
	}

	/**
	 * Returns whether transaction can be added to the blockchain.
	 * <p>
	 * Checks if transaction can have {@link TransactionHandler#process()} called.
	 * <p>
	 * Expected to be called within an ongoing SQL Transaction, typically by {@link Block#process()}.
	 * <p>
	 * Transactions that have already been processed will return false.
	 * 
	 * @return true if transaction can be processed, false otherwise
	 */
	public abstract ValidationResult isValid();

	/**
	 * Actually process a transaction, updating the blockchain.
	 * <p>
	 * Processes transaction, updating balances, references, assets, etc. as appropriate.
	 * <p>
	 * Expected to be called within an ongoing SQL Transaction, typically by {@link Block#process()}.
	 */
	public abstract void process();

	/**
	 * Undo transaction, updating the blockchain.
	 * <p>
	 * Undoes transaction, updating balances, references, assets, etc. as appropriate.
	 * <p>
	 * Expected to be called within an ongoing SQL Transaction, typically by {@link Block#process()}.
	 */
	public abstract void orphan();

}
