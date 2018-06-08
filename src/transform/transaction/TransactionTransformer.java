package transform.transaction;

import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import data.transaction.Transaction;
import data.transaction.Transaction.TransactionType;
import transform.TransformationException;
import transform.Transformer;
import utils.Base58;

public class TransactionTransformer extends Transformer {

	protected static final int TYPE_LENGTH = INT_LENGTH;

	public static Transaction fromBytes(byte[] bytes) throws TransformationException {
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

			default:
				return null;
		}
	}

	public static int getDataLength(Transaction transaction) throws TransformationException {
		switch (transaction.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.getDataLength(transaction);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static byte[] toBytes(Transaction transaction) throws TransformationException {
		switch (transaction.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toBytes(transaction);

			default:
				return null;
		}
	}

	public static JSONObject toJSON(Transaction transaction) throws TransformationException {
		switch (transaction.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toJSON(transaction);

			default:
				return null;
		}
	}

	@SuppressWarnings("unchecked")
	static JSONObject getBaseJSON(Transaction transaction) {
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
