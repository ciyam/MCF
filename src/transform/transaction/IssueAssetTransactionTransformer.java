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
import qora.transaction.IssueAssetTransaction;
import data.transaction.IssueAssetTransactionData;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class IssueAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ISSUER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int QUANTITY_LENGTH = LONG_LENGTH;
	private static final int IS_DIVISIBLE_LENGTH = BOOLEAN_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + ISSUER_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH
			+ QUANTITY_LENGTH + IS_DIVISIBLE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for IssueAssetTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] issuerPublicKey = Serialization.deserializePublicKey(byteBuffer);
		String owner = Serialization.deserializeRecipient(byteBuffer);

		String assetName = Serialization.deserializeSizedString(byteBuffer, IssueAssetTransaction.MAX_NAME_SIZE);
		String description = Serialization.deserializeSizedString(byteBuffer, IssueAssetTransaction.MAX_DESCRIPTION_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < QUANTITY_LENGTH + IS_DIVISIBLE_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for IssueAssetTransaction");

		long quantity = byteBuffer.getLong();
		boolean isDivisible = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new IssueAssetTransactionData(issuerPublicKey, owner, assetName, description, quantity, isDivisible, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

		return TYPE_LENGTH + TYPELESS_LENGTH + issueAssetTransactionData.getAssetName().length() + issueAssetTransactionData.getDescription().length();
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(issueAssetTransactionData.getType().value));
			bytes.write(Longs.toByteArray(issueAssetTransactionData.getTimestamp()));
			bytes.write(issueAssetTransactionData.getReference());

			bytes.write(issueAssetTransactionData.getIssuerPublicKey());
			bytes.write(Base58.decode(issueAssetTransactionData.getOwner()));
			Serialization.serializeSizedString(bytes, issueAssetTransactionData.getAssetName());
			Serialization.serializeSizedString(bytes, issueAssetTransactionData.getDescription());
			bytes.write(Longs.toByteArray(issueAssetTransactionData.getQuantity()));
			bytes.write((byte) (issueAssetTransactionData.getIsDivisible() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, issueAssetTransactionData.getFee());
			bytes.write(issueAssetTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

			byte[] issuerPublicKey = issueAssetTransactionData.getIssuerPublicKey();

			json.put("issuer", PublicKeyAccount.getAddress(issuerPublicKey));
			json.put("issuerPublicKey", HashCode.fromBytes(issuerPublicKey).toString());
			json.put("owner", issueAssetTransactionData.getOwner());
			json.put("assetName", issueAssetTransactionData.getAssetName());
			json.put("description", issueAssetTransactionData.getDescription());
			json.put("quantity", issueAssetTransactionData.getQuantity());
			json.put("isDivisible", issueAssetTransactionData.getIsDivisible());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
