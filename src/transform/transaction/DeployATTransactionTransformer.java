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

import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.transaction.DeployATTransaction;
import data.transaction.DeployATTransactionData;
import transform.TransformationException;
import utils.Serialization;

public class DeployATTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int AT_TYPE_SIZE_LENGTH = INT_LENGTH;
	private static final int TAGS_SIZE_LENGTH = INT_LENGTH;
	private static final int CREATION_BYTES_SIZE_LENGTH = INT_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + AT_TYPE_SIZE_LENGTH
			+ TAGS_SIZE_LENGTH + CREATION_BYTES_SIZE_LENGTH + AMOUNT_LENGTH;
	private static final int V4_TYPELESS_LENGTH = TYPELESS_LENGTH + ASSET_ID_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int version = DeployATTransaction.getVersionByTimestamp(timestamp);

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, DeployATTransaction.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, DeployATTransaction.MAX_DESCRIPTION_SIZE);

		String ATType = Serialization.deserializeSizedString(byteBuffer, DeployATTransaction.MAX_AT_TYPE_SIZE);

		String tags = Serialization.deserializeSizedString(byteBuffer, DeployATTransaction.MAX_TAGS_SIZE);

		int creationBytesSize = byteBuffer.getInt();
		if (creationBytesSize <= 0 || creationBytesSize > DeployATTransaction.MAX_CREATION_BYTES_SIZE)
			throw new TransformationException("Creation bytes size invalid in DeployAT transaction");

		byte[] creationBytes = new byte[creationBytesSize];
		byteBuffer.get(creationBytes);

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		long assetId = Asset.QORA;
		if (version >= 4)
			assetId = byteBuffer.getLong();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new DeployATTransactionData(creatorPublicKey, name, description, ATType, tags, creationBytes, amount, assetId, fee, timestamp, reference,
				signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		DeployATTransactionData deployATTransactionData = (DeployATTransactionData) transactionData;

		int dataLength = TYPE_LENGTH;

		int version = DeployATTransaction.getVersionByTimestamp(transactionData.getTimestamp());

		if (version >= 4)
			dataLength += V4_TYPELESS_LENGTH;
		else
			dataLength += TYPELESS_LENGTH;

		dataLength += Utf8.encodedLength(deployATTransactionData.getName()) + Utf8.encodedLength(deployATTransactionData.getDescription())
				+ Utf8.encodedLength(deployATTransactionData.getATType()) + Utf8.encodedLength(deployATTransactionData.getTags())
				+ deployATTransactionData.getCreationBytes().length;

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			DeployATTransactionData deployATTransactionData = (DeployATTransactionData) transactionData;

			int version = DeployATTransaction.getVersionByTimestamp(transactionData.getTimestamp());

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(deployATTransactionData.getType().value));
			bytes.write(Longs.toByteArray(deployATTransactionData.getTimestamp()));
			bytes.write(deployATTransactionData.getReference());

			bytes.write(deployATTransactionData.getCreatorPublicKey());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getName());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getDescription());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getATType());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getTags());

			byte[] creationBytes = deployATTransactionData.getCreationBytes();
			bytes.write(Ints.toByteArray(creationBytes.length));
			bytes.write(creationBytes);

			Serialization.serializeBigDecimal(bytes, deployATTransactionData.getAmount());

			if (version >= 4)
				bytes.write(Longs.toByteArray(deployATTransactionData.getAssetId()));

			Serialization.serializeBigDecimal(bytes, deployATTransactionData.getFee());

			if (deployATTransactionData.getSignature() != null)
				bytes.write(deployATTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification omit AT-type and tags so we need to test for v1-ness and adjust the bytes accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		if (transactionData.getTimestamp() >= BlockChain.getQoraV2Timestamp())
			return TransactionTransformer.toBytesForSigningImpl(transactionData);

		// Special v1 version

		// Easier to start from scratch
		try {
			DeployATTransactionData deployATTransactionData = (DeployATTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(deployATTransactionData.getType().value));
			bytes.write(Longs.toByteArray(deployATTransactionData.getTimestamp()));
			bytes.write(deployATTransactionData.getReference());

			bytes.write(deployATTransactionData.getCreatorPublicKey());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getName());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getDescription());

			// Omitted: Serialization.serializeSizedString(bytes, deployATTransactionData.getATType());

			// Omitted: Serialization.serializeSizedString(bytes, deployATTransactionData.getTags());

			byte[] creationBytes = deployATTransactionData.getCreationBytes();
			bytes.write(Ints.toByteArray(creationBytes.length));
			bytes.write(creationBytes);

			Serialization.serializeBigDecimal(bytes, deployATTransactionData.getAmount());

			Serialization.serializeBigDecimal(bytes, deployATTransactionData.getFee());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			DeployATTransactionData deployATTransactionData = (DeployATTransactionData) transactionData;

			byte[] creatorPublicKey = deployATTransactionData.getCreatorPublicKey();

			json.put("creator", PublicKeyAccount.getAddress(creatorPublicKey));
			json.put("creatorPublicKey", HashCode.fromBytes(creatorPublicKey).toString());
			json.put("name", deployATTransactionData.getName());
			json.put("description", deployATTransactionData.getDescription());
			json.put("atType", deployATTransactionData.getATType());
			json.put("tags", deployATTransactionData.getTags());
			json.put("creationBytes", HashCode.fromBytes(deployATTransactionData.getCreationBytes()).toString());
			json.put("amount", deployATTransactionData.getAmount().toPlainString());
			json.put("assetId", deployATTransactionData.getAssetId());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
