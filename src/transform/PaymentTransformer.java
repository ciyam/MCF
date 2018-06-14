package transform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.primitives.Longs;

import data.PaymentData;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class PaymentTransformer extends Transformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;

	private static final int TOTAL_LENGTH = RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH;

	public static PaymentData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TOTAL_LENGTH)
			throw new TransformationException("Byte data too short for payment information");

		String recipient = Serialization.deserializeRecipient(byteBuffer);
		long assetId = byteBuffer.getLong();
		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		return new PaymentData(recipient, assetId, amount);
	}

	public static int getDataLength() throws TransformationException {
		return TOTAL_LENGTH;
	}

	public static byte[] toBytes(PaymentData paymentData) throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Base58.decode(paymentData.getRecipient()));
			bytes.write(Longs.toByteArray(paymentData.getAssetId()));
			Serialization.serializeBigDecimal(bytes, paymentData.getAmount());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(PaymentData paymentData) throws TransformationException {
		JSONObject json = new JSONObject();

		try {
			json.put("recipient", paymentData.getRecipient());

			// For gen1 support:
			json.put("asset", paymentData.getAssetId());
			// Gen2 version:
			json.put("assetId", paymentData.getAssetId());

			json.put("amount", paymentData.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
