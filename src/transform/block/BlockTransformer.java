package transform.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.transaction.Transaction;
import repository.DataException;
import transform.TransformationException;
import transform.Transformer;
import transform.transaction.TransactionTransformer;
import utils.Base58;
import utils.Serialization;

public class BlockTransformer extends Transformer {

	private static final int VERSION_LENGTH = INT_LENGTH;
	private static final int TRANSACTIONS_SIGNATURE_LENGTH = SIGNATURE_LENGTH;
	private static final int GENERATOR_SIGNATURE_LENGTH = SIGNATURE_LENGTH;
	private static final int BLOCK_REFERENCE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	private static final int TIMESTAMP_LENGTH = LONG_LENGTH;
	private static final int GENERATING_BALANCE_LENGTH = LONG_LENGTH;
	private static final int GENERATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int TRANSACTION_COUNT_LENGTH = INT_LENGTH;

	private static final int BASE_LENGTH = VERSION_LENGTH + BLOCK_REFERENCE_LENGTH + TIMESTAMP_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH
			+ TRANSACTIONS_SIGNATURE_LENGTH + GENERATOR_SIGNATURE_LENGTH + TRANSACTION_COUNT_LENGTH;

	protected static final int BLOCK_SIGNATURE_LENGTH = GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH;
	protected static final int TRANSACTION_SIZE_LENGTH = INT_LENGTH; // per transaction
	protected static final int AT_BYTES_LENGTH = INT_LENGTH;
	protected static final int AT_FEES_LENGTH = LONG_LENGTH;
	protected static final int AT_LENGTH = AT_FEES_LENGTH + AT_BYTES_LENGTH;

	public static BlockData fromBytes(byte[] bytes) throws TransformationException {
		if (bytes == null)
			return null;

		if (bytes.length < BASE_LENGTH)
			throw new TransformationException("Byte data too short for Block");

		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		int version = byteBuffer.getInt();

		if (version >= 2 && bytes.length < BASE_LENGTH + AT_LENGTH)
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

		byte[] atBytes = null;
		BigDecimal atFees = null;
		if (version >= 2) {
			int atBytesLength = byteBuffer.getInt();

			if (atBytesLength > Block.MAX_BLOCK_BYTES)
				throw new TransformationException("Byte data too long for Block's AT info");

			atBytes = new byte[atBytesLength];
			byteBuffer.get(atBytes);

			atFees = BigDecimal.valueOf(byteBuffer.getLong()).setScale(8);
		}

		int transactionCount = byteBuffer.getInt();

		// Parse transactions now, compared to deferred parsing in Gen1, so we can throw ParseException if need be
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

			TransactionData transaction = TransactionTransformer.fromBytes(transactionBytes);
			transactions.add(transaction);
		}

		if (byteBuffer.hasRemaining())
			throw new TransformationException("Excess byte data found after parsing Block");

		// XXX Can't return a simple BlockData object because it doesn't support holding the transactions
		// return new BlockData(version, reference, timestamp, generatingBalance, generatorPublicKey, generatorSignature, transactionsSignature, atBytes, atFees, transactions);
		return null;
	}

	public static int getDataLength(BlockData blockData) throws TransformationException {
		// TODO
		int blockLength = BASE_LENGTH;

		if (blockData.getVersion() >= 2 && blockData.getAtBytes() != null)
			blockLength += AT_FEES_LENGTH + AT_BYTES_LENGTH + blockData.getAtBytes().length;

		/*
		 *  XXX Where do the transactions come from? A param? Do we pass a Block instead of BlockData?
		// Short cut for no transactions
		if (block.getTransactions() == null || block.getTransactions().isEmpty())
			return blockLength;

		for (TransactionData transaction : this.transactions)
			blockLength += TRANSACTION_SIZE_LENGTH + transaction.getDataLength();
		*/

		return blockLength;
	}

	public static byte[] toBytes(BlockData blockData) throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength(blockData));

			bytes.write(Ints.toByteArray(blockData.getVersion()));
			bytes.write(Longs.toByteArray(blockData.getTimestamp()));
			bytes.write(blockData.getReference());
			// NOTE: generatingBalance serialized as long value, not as BigDecimal, for historic compatibility
			bytes.write(Longs.toByteArray(blockData.getGeneratingBalance().longValue()));
			bytes.write(blockData.getGeneratorPublicKey());
			bytes.write(blockData.getTransactionsSignature());
			bytes.write(blockData.getGeneratorSignature());

			if (blockData.getVersion() >= 2) {
				byte[] atBytes = blockData.getAtBytes();

				if (atBytes != null) {
					bytes.write(Ints.toByteArray(atBytes.length));
					bytes.write(atBytes);
					// NOTE: atFees serialized as long value, not as BigDecimal, for historic compatibility
					bytes.write(Longs.toByteArray(blockData.getAtFees().longValue()));
				} else {
					bytes.write(Ints.toByteArray(0));
					bytes.write(Longs.toByteArray(0L));
				}
			}

			// Transactions
			bytes.write(Ints.toByteArray(blockData.getTransactionCount()));

			/*
			 *  XXX Where do the transactions come from? A param? Do we pass a Block instead of BlockData?
			for (TransactionData transaction : blockData.getTransactions()) {
				bytes.write(Ints.toByteArray(transaction.getDataLength()));
				bytes.write(transaction.toBytes());
			}
			*/

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(BlockData blockData) throws TransformationException {
		JSONObject json = new JSONObject();

		json.put("version", blockData.getVersion());
		json.put("timestamp", blockData.getTimestamp());
		json.put("generatingBalance", blockData.getGeneratingBalance());
		json.put("generator", new PublicKeyAccount(blockData.getGeneratorPublicKey()).getAddress());
		json.put("generatorPublicKey", Base58.encode(blockData.getGeneratorPublicKey()));
		json.put("fee", blockData.getTotalFees().toPlainString());
		json.put("transactionsSignature", Base58.encode(blockData.getTransactionsSignature()));
		json.put("generatorSignature", Base58.encode(blockData.getGeneratorSignature()));
		json.put("signature", Base58.encode(blockData.getSignature()));

		if (blockData.getReference() != null)
			json.put("reference", Base58.encode(blockData.getReference()));

		json.put("height", blockData.getHeight());

		// Add transaction info
		JSONArray transactionsJson = new JSONArray();
		boolean tradesHappened = false;

		/*
		 *  XXX Where do the transactions come from? A param? Do we pass a Block instead of BlockData?
		for (TransactionData transaction : blockData.getTransactions()) {
			transactionsJson.add(transaction.toJSON());

			// If this is an asset CreateOrderTransaction then check to see if any trades happened
			if (transaction.getType() == Transaction.TransactionType.CREATE_ASSET_ORDER) {
				CreateOrderTransaction orderTransaction = (CreateOrderTransaction) transaction;
				Order order = orderTransaction.getOrder();
				List<Trade> trades = order.getTrades();

				// Filter out trades with timestamps that don't match order transaction's timestamp
				trades.removeIf((Trade trade) -> trade.getTimestamp() != order.getTimestamp());

				// Any trades left?
				if (!trades.isEmpty()) {
					tradesHappened = true;

					// No need to check any further
					break;
				}
			}
		}
		json.put("transactions", transactionsJson);
		*/

		// Add asset trade activity flag
		json.put("assetTrades", tradesHappened);

		// Add CIYAM AT info (if any)
		if (blockData.getAtBytes() != null) {
			json.put("blockATs", HashCode.fromBytes(blockData.getAtBytes()).toString());
			json.put("atFees", blockData.getAtFees());
		}

		return json;
	}

	public static byte[] getBytesForGeneratorSignature(BlockData blockData) throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH);

			// Only copy the generator signature from reference, which is the first 64 bytes.
			bytes.write(Arrays.copyOf(blockData.getReference(), GENERATOR_SIGNATURE_LENGTH));

			bytes.write(Longs.toByteArray(blockData.getGeneratingBalance().longValue()));

			// We're padding here just in case the generator is the genesis account whose public key is only 8 bytes long.
			bytes.write(Bytes.ensureCapacity(blockData.getGeneratorPublicKey(), GENERATOR_LENGTH, 0));

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new TransformationException(e);
		}
	}

	public static byte[] getBytesForTransactionsSignature(Block block) throws TransformationException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(GENERATOR_SIGNATURE_LENGTH + block.getBlockData().getTransactionCount() * TransactionTransformer.SIGNATURE_LENGTH);

		try {
			bytes.write(block.getBlockData().getGeneratorSignature());

			for (Transaction transaction : block.getTransactions()) {
				if (!transaction.isSignatureValid())
					return null;

				bytes.write(transaction.getTransactionData().getSignature());
			}

			return bytes.toByteArray();
		} catch (IOException | DataException e) {
			throw new TransformationException(e);
		}
	}
	
}
