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

import data.assets.TradeData;
import data.block.BlockData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.assets.Order;
import qora.block.Block;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.Transaction;
import repository.DataException;
import transform.TransformationException;
import transform.Transformer;
import transform.transaction.TransactionTransformer;
import utils.Base58;
import utils.Pair;
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

	/**
	 * Extract block data and transaction data from serialized bytes.
	 * 
	 * @param bytes
	 * @return BlockData and a List of transactions.
	 * @throws TransformationException
	 */
	public static Pair<BlockData, List<TransactionData>> fromBytes(byte[] bytes) throws TransformationException {
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

		// Parse transactions now, compared to deferred parsing in Gen1, so we can throw ParseException if need be.
		List<TransactionData> transactions = new ArrayList<TransactionData>();
		BigDecimal totalFees = BigDecimal.ZERO.setScale(8);
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

			totalFees.add(transactionData.getFee());
		}

		if (byteBuffer.hasRemaining())
			throw new TransformationException("Excess byte data found after parsing Block");

		// XXX we don't know height!
		int height = 0;
		BlockData blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generatorPublicKey, generatorSignature, atBytes, atFees);

		return new Pair<BlockData, List<TransactionData>>(blockData, transactions);
	}

	public static int getDataLength(Block block) throws TransformationException {
		BlockData blockData = block.getBlockData();
		int blockLength = BASE_LENGTH;

		if (blockData.getVersion() >= 2 && blockData.getAtBytes() != null)
			blockLength += AT_FEES_LENGTH + AT_BYTES_LENGTH + blockData.getAtBytes().length;

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

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(Block block) throws TransformationException {
		BlockData blockData = block.getBlockData();

		JSONObject json = new JSONObject();

		json.put("version", blockData.getVersion());
		json.put("timestamp", blockData.getTimestamp());
		json.put("generatingBalance", blockData.getGeneratingBalance());
		json.put("generator", PublicKeyAccount.getAddress(blockData.getGeneratorPublicKey()));
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

		// XXX this should be moved out to API as it requires repository access
		boolean tradesHappened = false;

		try {
			for (Transaction transaction : block.getTransactions()) {
				transactionsJson.add(TransactionTransformer.toJSON(transaction.getTransactionData()));

				// If this is an asset CreateOrderTransaction then check to see if any trades happened
				if (transaction.getTransactionData().getType() == Transaction.TransactionType.CREATE_ASSET_ORDER) {
					CreateOrderTransaction orderTransaction = (CreateOrderTransaction) transaction;
					Order order = orderTransaction.getOrder();
					List<TradeData> trades = order.getTrades();

					// Filter out trades with initiatingOrderId that doesn't match this order
					trades.removeIf((TradeData tradeData) -> !Arrays.equals(tradeData.getInitiator(), order.getOrderData().getOrderId()));

					// Any trades left?
					if (!trades.isEmpty()) {
						tradesHappened = true;
						// No need to check any further
						break;
					}
				}
			}
		} catch (DataException e) {
			throw new TransformationException("Unable to transform block into JSON", e);
		}
		json.put("transactions", transactionsJson);

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
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(
				GENERATOR_SIGNATURE_LENGTH + block.getBlockData().getTransactionCount() * TransactionTransformer.SIGNATURE_LENGTH);

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
