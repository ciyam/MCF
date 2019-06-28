package org.qora.at;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.ciyam.at.API;
import org.ciyam.at.ExecutionException;
import org.ciyam.at.FunctionData;
import org.ciyam.at.IllegalFunctionCodeException;
import org.ciyam.at.MachineState;
import org.ciyam.at.OpCode;
import org.ciyam.at.Timestamp;
import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.at.ATData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.ATTransaction;

import com.google.common.primitives.Bytes;

public class QoraATAPI extends API {

	// Useful constants
	private static final BigDecimal FEE_PER_STEP = BigDecimal.valueOf(1.0).setScale(8); // 1 Qora per "step"
	private static final int MAX_STEPS_PER_ROUND = 500;
	private static final int STEPS_PER_FUNCTION_CALL = 10;
	private static final int MINUTES_PER_BLOCK = 10;

	// Properties
	Repository repository;
	ATData atData;
	long blockTimestamp;

	/** List of generated AT transactions */
	List<ATTransaction> transactions;

	// Constructors

	public QoraATAPI(Repository repository, ATData atData, long blockTimestamp) {
		this.repository = repository;
		this.atData = atData;
		this.transactions = new ArrayList<ATTransaction>();
		this.blockTimestamp = blockTimestamp;
	}

	// Methods specific to Qora AT processing, not inherited

	public List<ATTransaction> getTransactions() {
		return this.transactions;
	}

	public BigDecimal calcFinalFees(MachineState state) {
		return FEE_PER_STEP.multiply(BigDecimal.valueOf(state.getSteps()));
	}

	// Inherited methods from CIYAM AT API

	@Override
	public int getMaxStepsPerRound() {
		return MAX_STEPS_PER_ROUND;
	}

	@Override
	public int getOpCodeSteps(OpCode opcode) {
		if (opcode.value >= OpCode.EXT_FUN.value && opcode.value <= OpCode.EXT_FUN_RET_DAT_2.value)
			return STEPS_PER_FUNCTION_CALL;

		return 1;
	}

	@Override
	public long getFeePerStep() {
		return FEE_PER_STEP.unscaledValue().longValue();
	}

	@Override
	public int getCurrentBlockHeight() {
		try {
			return this.repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch current blockchain height?", e);
		}
	}

	@Override
	public int getATCreationBlockHeight(MachineState state) {
		try {
			return this.repository.getATRepository().getATCreationBlockHeight(this.atData.getATAddress());
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's creation block height?", e);
		}
	}

	@Override
	public void putPreviousBlockHashInA(MachineState state) {
		try {
			BlockData blockData = this.repository.getBlockRepository().fromHeight(this.getPreviousBlockHeight());

			// Block's signature is 128 bytes so we need to reduce this to 4 longs (32 bytes)
			byte[] blockHash = Crypto.digest(blockData.getSignature());

			this.setA(state, blockHash);
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch previous block?", e);
		}
	}

	@Override
	public void putTransactionAfterTimestampInA(Timestamp timestamp, MachineState state) {
		// Recipient is this AT
		String recipient = this.atData.getATAddress();

		BlockchainAPI blockchainAPI = BlockchainAPI.valueOf(timestamp.blockchainId);
		blockchainAPI.putTransactionFromRecipientAfterTimestampInA(recipient, timestamp, state);
	}

	@Override
	public long getTypeFromTransactionInA(MachineState state) {
		TransactionData transactionData = this.fetchTransaction(state);

		switch (transactionData.getType()) {
			case PAYMENT:
				return ATTransactionType.PAYMENT.value;

			case MESSAGE:
				return ATTransactionType.MESSAGE.value;

			case AT:
				if (((ATTransactionData) transactionData).getAmount() != null)
					return ATTransactionType.PAYMENT.value;
				else
					return ATTransactionType.MESSAGE.value;

			default:
				return 0xffffffffffffffffL;
		}
	}

	@Override
	public long getAmountFromTransactionInA(MachineState state) {
		Timestamp timestamp = new Timestamp(state.getA1());
		BlockchainAPI blockchainAPI = BlockchainAPI.valueOf(timestamp.blockchainId);
		return blockchainAPI.getAmountFromTransactionInA(timestamp, state);
	}

	@Override
	public long getTimestampFromTransactionInA(MachineState state) {
		// Transaction's "timestamp" already stored in A1
		Timestamp timestamp = new Timestamp(state.getA1());
		return timestamp.longValue();
	}

	@Override
	public long generateRandomUsingTransactionInA(MachineState state) {
		// The plan here is to sleep for a block then use next block's signature and this transaction's signature to generate pseudo-random, but deterministic,
		// value.

		if (!isFirstOpCodeAfterSleeping(state)) {
			// First call

			// Sleep for a block
			this.setIsSleeping(state, true);

			return 0L; // not used
		} else {
			// Second call

			// HASH(A and new block hash)
			TransactionData transactionData = this.fetchTransaction(state);

			try {
				BlockData blockData = this.repository.getBlockRepository().getLastBlock();

				if (blockData == null)
					throw new RuntimeException("AT API unable to fetch latest block?");

				byte[] input = Bytes.concat(transactionData.getSignature(), blockData.getSignature());

				byte[] hash = Crypto.digest(input);

				return fromBytes(hash, 0);
			} catch (DataException e) {
				throw new RuntimeException("AT API unable to fetch latest block from repository?", e);
			}
		}
	}

	@Override
	public void putMessageFromTransactionInAIntoB(MachineState state) {
		// Zero B in case of issues or shorter-than-B message
		this.zeroB(state);

		TransactionData transactionData = this.fetchTransaction(state);

		byte[] messageData = null;

		switch (transactionData.getType()) {
			case MESSAGE:
				messageData = ((MessageTransactionData) transactionData).getData();
				break;

			case AT:
				messageData = ((ATTransactionData) transactionData).getMessage();
				break;

			default:
				return;
		}

		// Check data length is appropriate, i.e. not larger than B
		if (messageData.length > 4 * 8)
			return;

		// Pad messageData to fit B
		byte[] paddedMessageData = Bytes.ensureCapacity(messageData, 4 * 8, 0);

		// Endian must be correct here so that (for example) a SHA256 message can be compared to one generated locally
		this.setB(state, paddedMessageData);
	}

	@Override
	public void putAddressFromTransactionInAIntoB(MachineState state) {
		TransactionData transactionData = this.fetchTransaction(state);

		// We actually use public key as it has more potential utility (e.g. message verification) than an address
		byte[] bytes = transactionData.getCreatorPublicKey();

		this.setB(state, bytes);
	}

	@Override
	public void putCreatorAddressIntoB(MachineState state) {
		// We actually use public key as it has more potential utility (e.g. message verification) than an address
		byte[] bytes = atData.getCreatorPublicKey();

		this.setB(state, bytes);
	}

	@Override
	public long getCurrentBalance(MachineState state) {
		Account atAccount = this.getATAccount();

		try {
			return atAccount.getConfirmedBalance(Asset.QORA).unscaledValue().longValue();
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's current balance?", e);
		}
	}

	@Override
	public void payAmountToB(long unscaledAmount, MachineState state) {
		byte[] publicKey = state.getB();

		PublicKeyAccount recipient = new PublicKeyAccount(this.repository, publicKey);

		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();
		BigDecimal amount = BigDecimal.valueOf(unscaledAmount, 8);

		ATTransactionData atTransactionData = new ATTransactionData(this.atData.getATAddress(), recipient.getAddress(), amount, this.atData.getAssetId(),
				new byte[0], BigDecimal.ZERO.setScale(8), timestamp, reference);
		ATTransaction atTransaction = new ATTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public void messageAToB(MachineState state) {
		byte[] message = state.getA();
		byte[] publicKey = state.getB();

		PublicKeyAccount recipient = new PublicKeyAccount(this.repository, publicKey);

		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();

		ATTransactionData atTransactionData = new ATTransactionData(this.atData.getATAddress(), recipient.getAddress(), BigDecimal.ZERO,
				this.atData.getAssetId(), message, BigDecimal.ZERO.setScale(8), timestamp, reference);
		ATTransaction atTransaction = new ATTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public long addMinutesToTimestamp(Timestamp timestamp, long minutes, MachineState state) {
		int blockHeight = timestamp.blockHeight;

		// At least one block in the future
		blockHeight += (minutes / MINUTES_PER_BLOCK) + 1;

		return new Timestamp(blockHeight, 0).longValue();
	}

	@Override
	public void onFinished(long finalBalance, MachineState state) {
		// Refund remaining balance (if any) to AT's creator
		Account creator = this.getCreator();
		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();
		BigDecimal amount = BigDecimal.valueOf(finalBalance, 8);

		ATTransactionData atTransactionData = new ATTransactionData(this.atData.getATAddress(), creator.getAddress(), amount, this.atData.getAssetId(),
				new byte[0], BigDecimal.ZERO.setScale(8), timestamp, reference);
		ATTransaction atTransaction = new ATTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public void onFatalError(MachineState state, ExecutionException e) {
		state.getLogger().error("AT " + this.atData.getATAddress() + " suffered fatal error: " + e.getMessage());
	}

	@Override
	public void platformSpecificPreExecuteCheck(int paramCount, boolean returnValueExpected, MachineState state, short rawFunctionCode)
			throws IllegalFunctionCodeException {
		QoraFunctionCode qoraFunctionCode = QoraFunctionCode.valueOf(rawFunctionCode);

		if (qoraFunctionCode == null)
			throw new IllegalFunctionCodeException("Unknown Qora function code 0x" + String.format("%04x", rawFunctionCode) + " encountered");

		qoraFunctionCode.preExecuteCheck(2, true, state, rawFunctionCode);
	}

	@Override
	public void platformSpecificPostCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
		QoraFunctionCode qoraFunctionCode = QoraFunctionCode.valueOf(rawFunctionCode);

		qoraFunctionCode.execute(functionData, state, rawFunctionCode);
	}

	// Utility methods

	/** Convert part of little-endian byte[] to long */
	/* package */ static long fromBytes(byte[] bytes, int start) {
		return (bytes[start] & 0xffL) | (bytes[start + 1] & 0xffL) << 8 | (bytes[start + 2] & 0xffL) << 16 | (bytes[start + 3] & 0xffL) << 24
				| (bytes[start + 4] & 0xffL) << 32 | (bytes[start + 5] & 0xffL) << 40 | (bytes[start + 6] & 0xffL) << 48 | (bytes[start + 7] & 0xffL) << 56;
	}

	/** Returns SHA2-192 digest of input - used to verify transaction signatures */
	public static byte[] sha192(byte[] input) {
		try {
			// SHA2-192
			MessageDigest sha192 = MessageDigest.getInstance("SHA-192");
			return sha192.digest(input);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-192 not available");
		}
	}

	/** Verify transaction's SHA2-192 hashed signature matches A2 thru A4 */
	private static void verifyTransaction(TransactionData transactionData, MachineState state) {
		// Compare SHA2-192 of transaction's signature against A2 thru A4
		byte[] hash = sha192(transactionData.getSignature());

		if (state.getA2() != fromBytes(hash, 0) || state.getA3() != fromBytes(hash, 8) || state.getA4() != fromBytes(hash, 16))
			throw new IllegalStateException("Transaction signature in A no longer matches signature from repository");
	}

	/** Returns transaction data from repository using block height & sequence from A1, checking the transaction signatures match too */
	/* package */ TransactionData fetchTransaction(MachineState state) {
		Timestamp timestamp = new Timestamp(state.getA1());

		try {
			TransactionData transactionData = this.repository.getTransactionRepository().fromHeightAndSequence(timestamp.blockHeight,
					timestamp.transactionSequence);

			if (transactionData == null)
				throw new RuntimeException("AT API unable to fetch transaction?");

			// Check transaction referenced still matches the one from the repository
			verifyTransaction(transactionData, state);

			return transactionData;
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch transaction type?", e);
		}
	}

	/** Returns AT's account */
	/* package */ Account getATAccount() {
		return new Account(this.repository, this.atData.getATAddress());
	}

	/** Returns AT's creator's account */
	private PublicKeyAccount getCreator() {
		return new PublicKeyAccount(this.repository, this.atData.getCreatorPublicKey());
	}

	/** Returns the timestamp to use for next AT Transaction */
	private long getNextTransactionTimestamp() {
		/*
		 * Timestamp is block's timestamp + position in AT-Transactions list.
		 * 
		 * We need increasing timestamps to preserve transaction order and hence a correct signature-reference chain when the block is processed.
		 * 
		 * As Qora blocks must share the same milliseconds component in their timestamps, this allows us to generate up to 1,000 AT-Transactions per AT without
		 * issue.
		 * 
		 * As long as ATs are not allowed to generate that many per block, e.g. by limiting maximum steps per execution round, then we should be fine.
		 */

		return this.blockTimestamp + this.transactions.size();
	}

	/** Returns AT account's lastReference, taking newly generated ATTransactions into account */
	private byte[] getLastReference() {
		// Use signature from last AT Transaction we generated
		if (!this.transactions.isEmpty())
			return this.transactions.get(this.transactions.size() - 1).getTransactionData().getSignature();

		// No transactions yet, so look up AT's account's last reference from repository
		Account atAccount = this.getATAccount();

		try {
			return atAccount.getLastReference();
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's last reference from repository?", e);
		}
	}

}
