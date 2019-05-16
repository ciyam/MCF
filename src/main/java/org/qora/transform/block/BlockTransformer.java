package org.qora.transform.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.account.PublicKeyAccount;
import org.qora.block.Block;
import org.qora.data.at.ATStateData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.transform.Transformer;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;
import org.qora.utils.Serialization;
import org.qora.utils.Triple;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class BlockTransformer extends Transformer {

	private static final int VERSION_LENGTH = INT_LENGTH;
	private static final int TRANSACTIONS_SIGNATURE_LENGTH = SIGNATURE_LENGTH;
	private static final int GENERATOR_SIGNATURE_LENGTH = SIGNATURE_LENGTH;
	private static final int BLOCK_REFERENCE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	private static final int TIMESTAMP_LENGTH = LONG_LENGTH;
	private static final int GENERATING_BALANCE_LENGTH = LONG_LENGTH;
	private static final int GENERATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int TRANSACTION_COUNT_LENGTH = INT_LENGTH;

	private static final int BASE_LENGTH = VERSION_LENGTH + TIMESTAMP_LENGTH + BLOCK_REFERENCE_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH
			+ TRANSACTIONS_SIGNATURE_LENGTH + GENERATOR_SIGNATURE_LENGTH + TRANSACTION_COUNT_LENGTH;

	public static final int BLOCK_SIGNATURE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	protected static final int TRANSACTION_SIZE_LENGTH = INT_LENGTH; // per transaction
	protected static final int AT_BYTES_LENGTH = INT_LENGTH;
	protected static final int AT_FEES_LENGTH = LONG_LENGTH;
	protected static final int AT_LENGTH = AT_FEES_LENGTH + AT_BYTES_LENGTH;

	protected static final int V2_AT_ENTRY_LENGTH = ADDRESS_LENGTH + MD5_LENGTH;
	protected static final int V4_AT_ENTRY_LENGTH = ADDRESS_LENGTH + SHA256_LENGTH + BIG_DECIMAL_LENGTH;

	/**
	 * Extract block data and transaction data from serialized bytes.
	 * 
	 * @param bytes
	 * @return BlockData and a List of transactions.
	 * @throws TransformationException
	 */
	public static Triple<BlockData, List<TransactionData>, List<ATStateData>> fromBytes(byte[] bytes) throws TransformationException {
		if (bytes == null)
			return null;

		if (bytes.length < BASE_LENGTH)
			throw new TransformationException("Byte data too short for Block");

		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		return fromByteBuffer(byteBuffer);
	}

	/**
	 * Extract block data and transaction data from serialized bytes.
	 * 
	 * @param bytes
	 * @return BlockData and a List of transactions.
	 * @throws TransformationException
	 */
	public static Triple<BlockData, List<TransactionData>, List<ATStateData>> fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		int version = byteBuffer.getInt();

		if (version >= 2 && byteBuffer.remaining() < BASE_LENGTH + AT_BYTES_LENGTH - VERSION_LENGTH)
			throw new TransformationException("Byte data too short for V2+ Block");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[BLOCK_REFERENCE_LENGTH];
		byteBuffer.get(reference);

		BigDecimal generatingBalance = BigDecimal.valueOf(byteBuffer.getLong()).setScale(8);

		byte[] generatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		byte[] transactionsSignature = new byte[TRANSACTIONS_SIGNATURE_LENGTH];
		byteBuffer.get(transactionsSignature);

		byte[] generatorSignature = new byte[GENERATOR_SIGNATURE_LENGTH];
		byteBuffer.get(generatorSignature);

		BigDecimal totalFees = BigDecimal.ZERO.setScale(8);

		int atCount = 0;
		BigDecimal atFees = BigDecimal.ZERO.setScale(8);
		List<ATStateData> atStates = new ArrayList<ATStateData>();

		if (version >= 2) {
			int atBytesLength = byteBuffer.getInt();

			if (atBytesLength > Block.MAX_BLOCK_BYTES)
				throw new TransformationException("Byte data too long for Block's AT info");

			ByteBuffer atByteBuffer = byteBuffer.slice();
			atByteBuffer.limit(atBytesLength);

			if (version < 4) {
				// For versions < 4, read AT-address & MD5 pairs
				if (atBytesLength % V2_AT_ENTRY_LENGTH != 0)
					throw new TransformationException("AT byte data not a multiple of version 2+ entries");

				while (atByteBuffer.hasRemaining()) {
					byte[] atAddressBytes = new byte[ADDRESS_LENGTH];
					atByteBuffer.get(atAddressBytes);
					String atAddress = Base58.encode(atAddressBytes);

					byte[] stateHash = new byte[MD5_LENGTH];
					atByteBuffer.get(stateHash);

					atStates.add(new ATStateData(atAddress, stateHash));
				}

				// Bump byteBuffer over AT states just read in slice
				byteBuffer.position(byteBuffer.position() + atBytesLength);

				// AT fees follow in versions < 4
				atFees = Serialization.deserializeBigDecimal(byteBuffer);
			} else {
				// For block versions >= 4, read AT-address, SHA256 hash and fees
				if (atBytesLength % V4_AT_ENTRY_LENGTH != 0)
					throw new TransformationException("AT byte data not a multiple of version 4+ entries");

				while (atByteBuffer.hasRemaining()) {
					byte[] atAddressBytes = new byte[ADDRESS_LENGTH];
					atByteBuffer.get(atAddressBytes);
					String atAddress = Base58.encode(atAddressBytes);

					byte[] stateHash = new byte[SHA256_LENGTH];
					atByteBuffer.get(stateHash);

					BigDecimal fees = Serialization.deserializeBigDecimal(atByteBuffer);
					// Add this AT's fees to our total
					atFees = atFees.add(fees);

					atStates.add(new ATStateData(atAddress, stateHash, fees));
				}
			}

			// AT count to reflect the number of states we have
			atCount = atStates.size();

			// Add AT fees to totalFees
			totalFees = totalFees.add(atFees);
		}

		int transactionCount = byteBuffer.getInt();

		// Parse transactions now, compared to deferred parsing in Gen1, so we can throw ParseException if need be.
		List<TransactionData> transactions = new ArrayList<TransactionData>();

		for (int t = 0; t < transactionCount; ++t) {
			if (byteBuffer.remaining() < TRANSACTION_SIZE_LENGTH)
				throw new TransformationException("Byte data too short for Block Transaction length");

			int transactionLength = byteBuffer.getInt();

			if (byteBuffer.remaining() < transactionLength)
				throw new TransformationException("Byte data too short for Block Transaction");

			if (transactionLength > Block.MAX_BLOCK_BYTES)
				throw new TransformationException("Byte data too long for Block Transaction");

			byte[] transactionBytes = new byte[transactionLength];
			byteBuffer.get(transactionBytes);

			TransactionData transactionData = TransactionTransformer.fromBytes(transactionBytes);
			transactions.add(transactionData);

			totalFees = totalFees.add(transactionData.getFee());
		}

		if (byteBuffer.hasRemaining())
			throw new TransformationException("Excess byte data found after parsing Block");

		// We don't have a height!
		Integer height = null;
		BlockData blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generatorPublicKey, generatorSignature, atCount, atFees);

		return new Triple<BlockData, List<TransactionData>, List<ATStateData>>(blockData, transactions, atStates);
	}

	public static int getDataLength(Block block) throws TransformationException {
		BlockData blockData = block.getBlockData();
		int blockLength = BASE_LENGTH;

		if (blockData.getVersion() >= 4)
			blockLength += AT_BYTES_LENGTH + blockData.getATCount() * V4_AT_ENTRY_LENGTH;
		else if (blockData.getVersion() >= 2)
			blockLength += AT_FEES_LENGTH + AT_BYTES_LENGTH + blockData.getATCount() * V2_AT_ENTRY_LENGTH;

		try {
			// Short cut for no transactions
			List<Transaction> transactions = block.getTransactions();
			if (transactions == null || transactions.isEmpty())
				return blockLength;

			for (Transaction transaction : transactions)
				blockLength += TRANSACTION_SIZE_LENGTH + TransactionTransformer.getDataLength(transaction.getTransactionData());
		} catch (DataException e) {
			throw new TransformationException("Unable to determine serialized block length", e);
		}

		return blockLength;
	}

	public static byte[] toBytes(Block block) throws TransformationException {
		BlockData blockData = block.getBlockData();

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength(block));

			bytes.write(Ints.toByteArray(blockData.getVersion()));
			bytes.write(Longs.toByteArray(blockData.getTimestamp()));
			bytes.write(blockData.getReference());
			// NOTE: generatingBalance serialized as long value, not as BigDecimal, for historic compatibility
			bytes.write(Longs.toByteArray(blockData.getGeneratingBalance().longValue()));
			bytes.write(blockData.getGeneratorPublicKey());
			bytes.write(blockData.getTransactionsSignature());
			bytes.write(blockData.getGeneratorSignature());

			if (blockData.getVersion() >= 4) {
				int atBytesLength = blockData.getATCount() * V4_AT_ENTRY_LENGTH;
				bytes.write(Ints.toByteArray(atBytesLength));

				for (ATStateData atStateData : block.getATStates()) {
					bytes.write(Base58.decode(atStateData.getATAddress()));
					bytes.write(atStateData.getStateHash());
					Serialization.serializeBigDecimal(bytes, atStateData.getFees());
				}
			} else if (blockData.getVersion() >= 2) {
				int atBytesLength = blockData.getATCount() * V2_AT_ENTRY_LENGTH;
				bytes.write(Ints.toByteArray(atBytesLength));

				for (ATStateData atStateData : block.getATStates()) {
					bytes.write(Base58.decode(atStateData.getATAddress()));
					bytes.write(atStateData.getStateHash());
				}

				if (blockData.getATFees() != null)
					// NOTE: atFees serialized as long value, not as BigDecimal, for historic compatibility
					bytes.write(Longs.toByteArray(blockData.getATFees().longValue()));
				else
					bytes.write(Longs.toByteArray(0));
			}

			// Transactions
			bytes.write(Ints.toByteArray(blockData.getTransactionCount()));

			for (Transaction transaction : block.getTransactions()) {
				TransactionData transactionData = transaction.getTransactionData();
				bytes.write(Ints.toByteArray(TransactionTransformer.getDataLength(transactionData)));
				bytes.write(TransactionTransformer.toBytes(transactionData));
			}

			return bytes.toByteArray();
		} catch (IOException | DataException e) {
			throw new TransformationException("Unable to serialize block", e);
		}
	}

	public static byte[] getReferenceGeneratorSignature(byte[] blockReference) {
		return Arrays.copyOf(blockReference, GENERATOR_SIGNATURE_LENGTH);
	}

	public static byte[] getBytesForGeneratorSignature(BlockData blockData) throws TransformationException {
		byte[] generatorSignature = getReferenceGeneratorSignature(blockData.getReference());
		PublicKeyAccount generator = new PublicKeyAccount(null, blockData.getGeneratorPublicKey());

		return getBytesForGeneratorSignature(generatorSignature, blockData.getTimestamp(), generator);
	}

	public static byte[] getBytesForGeneratorSignature(byte[] generatorSignature, long timestamp, PublicKeyAccount generator)
			throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + TIMESTAMP_LENGTH + GENERATOR_LENGTH);

			bytes.write(generatorSignature);

			bytes.write(Longs.toByteArray(timestamp));

			// We're padding here just in case the generator is the genesis account whose public key is only 8 bytes long.
			bytes.write(Bytes.ensureCapacity(generator.getPublicKey(), GENERATOR_LENGTH, 0));

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new TransformationException(e);
		}
	}

	public static byte[] getBytesForTransactionsSignature(Block block) throws TransformationException {
		try {
			List<Transaction> transactions = block.getTransactions();

			ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + transactions.size() * TransactionTransformer.SIGNATURE_LENGTH);

			bytes.write(block.getBlockData().getGeneratorSignature());

			for (Transaction transaction : transactions) {
				// We don't include AT-Transactions as AT-state/output is dealt with elsewhere in the block code
				if (transaction.getTransactionData().getType() == TransactionType.AT)
					continue;

				if (!transaction.isSignatureValid())
					throw new TransformationException("Transaction signature invalid when building block's transactions signature");

				bytes.write(transaction.getTransactionData().getSignature());
			}

			return bytes.toByteArray();
		} catch (IOException | DataException e) {
			throw new TransformationException(e);
		}
	}

}
