package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class CreateAssetOrderTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int AMOUNT_LENGTH = 12; // Not standard BIG_DECIMAL_LENGTH

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + (ASSET_ID_LENGTH + AMOUNT_LENGTH) * 2;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_ASSET_ORDER.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("order creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("ID of asset of offer", TransformationType.LONG);
		layout.add("ID of asset wanted", TransformationType.LONG);
		layout.add("amount of asset on offer", TransformationType.ASSET_QUANTITY);
		layout.add("amount of asset wanted per offered asset", TransformationType.ASSET_QUANTITY);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		long haveAssetId = byteBuffer.getLong();

		long wantAssetId = byteBuffer.getLong();

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer, AMOUNT_LENGTH);

		BigDecimal price = Serialization.deserializeBigDecimal(byteBuffer, AMOUNT_LENGTH);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CreateAssetOrderTransactionData(creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(createOrderTransactionData.getType().value));
			bytes.write(Longs.toByteArray(createOrderTransactionData.getTimestamp()));
			bytes.write(createOrderTransactionData.getReference());

			bytes.write(createOrderTransactionData.getCreatorPublicKey());
			bytes.write(Longs.toByteArray(createOrderTransactionData.getHaveAssetId()));
			bytes.write(Longs.toByteArray(createOrderTransactionData.getWantAssetId()));
			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getAmount(), AMOUNT_LENGTH);
			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getPrice(), AMOUNT_LENGTH);

			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getFee());

			if (createOrderTransactionData.getSignature() != null)
				bytes.write(createOrderTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification have mangled price so we need to test for v1-ness and adjust the bytes accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return TransactionTransformer.toBytesForSigningImpl(transactionData);

		// Special v1 version
		try {
			CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(createOrderTransactionData.getType().value));
			bytes.write(Longs.toByteArray(createOrderTransactionData.getTimestamp()));
			bytes.write(createOrderTransactionData.getReference());

			bytes.write(createOrderTransactionData.getCreatorPublicKey());
			bytes.write(Longs.toByteArray(createOrderTransactionData.getHaveAssetId()));
			bytes.write(Longs.toByteArray(createOrderTransactionData.getWantAssetId()));
			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getAmount(), AMOUNT_LENGTH);

			// This is the crucial difference
			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getPrice(), FEE_LENGTH);

			Serialization.serializeBigDecimal(bytes, createOrderTransactionData.getFee());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

			byte[] creatorPublicKey = createOrderTransactionData.getCreatorPublicKey();

			json.put("creator", PublicKeyAccount.getAddress(creatorPublicKey));
			json.put("creatorPublicKey", HashCode.fromBytes(creatorPublicKey).toString());

			JSONObject order = new JSONObject();
			order.put("have", createOrderTransactionData.getHaveAssetId());
			order.put("want", createOrderTransactionData.getWantAssetId());
			order.put("amount", createOrderTransactionData.getAmount().toPlainString());
			order.put("price", createOrderTransactionData.getPrice().toPlainString());

			json.put("order", order);
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
