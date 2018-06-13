package transform.transaction;

import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import data.transaction.TransactionData;
import qora.transaction.Transaction.TransactionType;
import transform.TransformationException;
import transform.Transformer;
import utils.Base58;

public class TransactionTransformer extends Transformer {

	protected static final int TYPE_LENGTH = INT_LENGTH;
	protected static final int REFERENCE_LENGTH = SIGNATURE_LENGTH;
	protected static final int BASE_TYPELESS_LENGTH = TYPE_LENGTH + TIMESTAMP_LENGTH + REFERENCE_LENGTH + SIGNATURE_LENGTH;

	public static TransactionData fromBytes(byte[] bytes) throws TransformationException {
		if (bytes == null)
			return null;

		if (bytes.length < TYPE_LENGTH)
			throw new TransformationException("Byte data too short to determine transaction type");

		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		TransactionType type = TransactionType.valueOf(byteBuffer.getInt());
		if (type == null)
			return null;

		switch (type) {
			case GENESIS:
				return GenesisTransactionTransformer.fromByteBuffer(byteBuffer);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.fromByteBuffer(byteBuffer);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.fromByteBuffer(byteBuffer);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		switch (transactionData.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.getDataLength(transactionData);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.getDataLength(transactionData);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.getDataLength(transactionData);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		switch (transactionData.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toBytes(transactionData);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.toBytes(transactionData);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.toBytes(transactionData);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static JSONObject toJSON(TransactionData transaction) throws TransformationException {
		switch (transaction.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toJSON(transaction);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.toJSON(transaction);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.toJSON(transaction);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	@SuppressWarnings("unchecked")
	static JSONObject getBaseJSON(TransactionData transaction) {
		JSONObject json = new JSONObject();

		json.put("type", transaction.getType().value);
		json.put("fee", transaction.getFee().toPlainString());
		json.put("timestamp", transaction.getTimestamp());
		json.put("signature", Base58.encode(transaction.getSignature()));

		byte[] reference = transaction.getReference();
		if (reference != null)
			json.put("reference", Base58.encode(reference));

		// XXX Can't do this as it requires database access:
		// json.put("confirmations", RepositoryManager.getTransactionRepository.getConfirmations(transaction));

		return json;
	}

}
