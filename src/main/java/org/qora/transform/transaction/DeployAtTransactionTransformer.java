package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.DeployAtTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.DeployAtTransaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class DeployAtTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int AT_TYPE_SIZE_LENGTH = INT_LENGTH;
	private static final int TAGS_SIZE_LENGTH = INT_LENGTH;
	private static final int CREATION_BYTES_SIZE_LENGTH = INT_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;

	private static final int EXTRAS_LENGTH = NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + AT_TYPE_SIZE_LENGTH + TAGS_SIZE_LENGTH + CREATION_BYTES_SIZE_LENGTH
			+ AMOUNT_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.DEPLOY_AT.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("AT creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("AT name length", TransformationType.INT);
		layout.add("AT name", TransformationType.STRING);
		layout.add("AT description length", TransformationType.INT);
		layout.add("AT description", TransformationType.STRING);
		layout.add("AT tags length", TransformationType.INT);
		layout.add("AT tags", TransformationType.STRING);
		layout.add("creation bytes length", TransformationType.INT);
		layout.add("creation bytes", TransformationType.DATA);
		layout.add("AT initial balance", TransformationType.AMOUNT);
		layout.add("asset ID used by AT", TransformationType.LONG);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int version = DeployAtTransaction.getVersionByTimestamp(timestamp);

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, DeployAtTransaction.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, DeployAtTransaction.MAX_DESCRIPTION_SIZE);

		String ATType = Serialization.deserializeSizedString(byteBuffer, DeployAtTransaction.MAX_AT_TYPE_SIZE);

		String tags = Serialization.deserializeSizedString(byteBuffer, DeployAtTransaction.MAX_TAGS_SIZE);

		int creationBytesSize = byteBuffer.getInt();
		if (creationBytesSize <= 0 || creationBytesSize > DeployAtTransaction.MAX_CREATION_BYTES_SIZE)
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

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new DeployAtTransactionData(baseTransactionData, name, description, ATType, tags, creationBytes, amount, assetId);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		DeployAtTransactionData deployATTransactionData = (DeployAtTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH;

		int version = DeployAtTransaction.getVersionByTimestamp(transactionData.getTimestamp());

		// V4+ have assetId too
		if (version >= 4)
			dataLength += ASSET_ID_LENGTH;

		dataLength += Utf8.encodedLength(deployATTransactionData.getName()) + Utf8.encodedLength(deployATTransactionData.getDescription())
				+ Utf8.encodedLength(deployATTransactionData.getAtType()) + Utf8.encodedLength(deployATTransactionData.getTags())
				+ deployATTransactionData.getCreationBytes().length;

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			DeployAtTransactionData deployATTransactionData = (DeployAtTransactionData) transactionData;

			int version = DeployAtTransaction.getVersionByTimestamp(transactionData.getTimestamp());

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeSizedString(bytes, deployATTransactionData.getName());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getDescription());

			Serialization.serializeSizedString(bytes, deployATTransactionData.getAtType());

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
		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return TransactionTransformer.toBytesForSigningImpl(transactionData);

		// Special v1 version

		// Easier to start from scratch
		try {
			DeployAtTransactionData deployATTransactionData = (DeployAtTransactionData) transactionData;

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

}
