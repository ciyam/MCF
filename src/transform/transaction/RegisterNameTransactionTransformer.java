package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.RegisterNameTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class RegisterNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int REGISTRANT_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + REGISTRANT_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DATA_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for RegisterNameTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] registrantPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeRecipient(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);
		String data = Serialization.deserializeSizedString(byteBuffer, Name.MAX_DATA_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for RegisterNameTransaction");

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new RegisterNameTransactionData(registrantPublicKey, owner, name, data, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(registerNameTransactionData.getName())
				+ Utf8.encodedLength(registerNameTransactionData.getData());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(registerNameTransactionData.getType().value));
			bytes.write(Longs.toByteArray(registerNameTransactionData.getTimestamp()));
			bytes.write(registerNameTransactionData.getReference());

			bytes.write(registerNameTransactionData.getRegistrantPublicKey());
			bytes.write(Base58.decode(registerNameTransactionData.getOwner()));
			Serialization.serializeSizedString(bytes, registerNameTransactionData.getName());
			Serialization.serializeSizedString(bytes, registerNameTransactionData.getData());

			Serialization.serializeBigDecimal(bytes, registerNameTransactionData.getFee());

			if (registerNameTransactionData.getSignature() != null)
				bytes.write(registerNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

			byte[] registrantPublicKey = registerNameTransactionData.getRegistrantPublicKey();

			json.put("registrant", PublicKeyAccount.getAddress(registrantPublicKey));
			json.put("registrantPublicKey", HashCode.fromBytes(registrantPublicKey).toString());

			json.put("owner", registerNameTransactionData.getOwner());
			json.put("name", registerNameTransactionData.getName());
			json.put("data", registerNameTransactionData.getData());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
