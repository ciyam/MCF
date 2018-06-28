package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import data.transaction.CreateOrderTransactionData;
import transform.TransformationException;
import utils.Serialization;

public class CreateOrderTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int AMOUNT_LENGTH = 12; // Not standard BIG_DECIMAL_LENGTH

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + (ASSET_ID_LENGTH + AMOUNT_LENGTH) * 2;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for CreateOrderTransaction");

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

		return new CreateOrderTransactionData(creatorPublicKey, haveAssetId, wantAssetId, amount, price, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreateOrderTransactionData createOrderTransactionData = (CreateOrderTransactionData) transactionData;

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

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CreateOrderTransactionData createOrderTransactionData = (CreateOrderTransactionData) transactionData;

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
