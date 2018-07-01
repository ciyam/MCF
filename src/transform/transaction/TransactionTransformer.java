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
	protected static final int FEE_LENGTH = BIG_DECIMAL_LENGTH;
	protected static final int BASE_TYPELESS_LENGTH = TIMESTAMP_LENGTH + REFERENCE_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH;

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

			case PAYMENT:
				return PaymentTransactionTransformer.fromByteBuffer(byteBuffer);

			case REGISTER_NAME:
				return RegisterNameTransactionTransformer.fromByteBuffer(byteBuffer);

			case UPDATE_NAME:
				return UpdateNameTransactionTransformer.fromByteBuffer(byteBuffer);

			case CREATE_POLL:
				return CreatePollTransactionTransformer.fromByteBuffer(byteBuffer);

			case VOTE_ON_POLL:
				return VoteOnPollTransactionTransformer.fromByteBuffer(byteBuffer);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.fromByteBuffer(byteBuffer);

			case TRANSFER_ASSET:
				return TransferAssetTransactionTransformer.fromByteBuffer(byteBuffer);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.fromByteBuffer(byteBuffer);

			case CANCEL_ASSET_ORDER:
				return CancelOrderTransactionTransformer.fromByteBuffer(byteBuffer);

			case MULTIPAYMENT:
				return MultiPaymentTransactionTransformer.fromByteBuffer(byteBuffer);

			case MESSAGE:
				return MessageTransactionTransformer.fromByteBuffer(byteBuffer);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		switch (transactionData.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.getDataLength(transactionData);

			case PAYMENT:
				return PaymentTransactionTransformer.getDataLength(transactionData);

			case REGISTER_NAME:
				return RegisterNameTransactionTransformer.getDataLength(transactionData);

			case UPDATE_NAME:
				return UpdateNameTransactionTransformer.getDataLength(transactionData);

			case CREATE_POLL:
				return CreatePollTransactionTransformer.getDataLength(transactionData);

			case VOTE_ON_POLL:
				return VoteOnPollTransactionTransformer.getDataLength(transactionData);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.getDataLength(transactionData);

			case TRANSFER_ASSET:
				return TransferAssetTransactionTransformer.getDataLength(transactionData);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.getDataLength(transactionData);

			case CANCEL_ASSET_ORDER:
				return CancelOrderTransactionTransformer.getDataLength(transactionData);

			case MULTIPAYMENT:
				return MultiPaymentTransactionTransformer.getDataLength(transactionData);

			case MESSAGE:
				return MessageTransactionTransformer.getDataLength(transactionData);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		switch (transactionData.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toBytes(transactionData);

			case PAYMENT:
				return PaymentTransactionTransformer.toBytes(transactionData);

			case REGISTER_NAME:
				return RegisterNameTransactionTransformer.toBytes(transactionData);

			case UPDATE_NAME:
				return UpdateNameTransactionTransformer.toBytes(transactionData);

			case CREATE_POLL:
				return CreatePollTransactionTransformer.toBytes(transactionData);

			case VOTE_ON_POLL:
				return VoteOnPollTransactionTransformer.toBytes(transactionData);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.toBytes(transactionData);

			case TRANSFER_ASSET:
				return TransferAssetTransactionTransformer.toBytes(transactionData);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.toBytes(transactionData);

			case CANCEL_ASSET_ORDER:
				return CancelOrderTransactionTransformer.toBytes(transactionData);

			case MULTIPAYMENT:
				return MultiPaymentTransactionTransformer.toBytes(transactionData);

			case MESSAGE:
				return MessageTransactionTransformer.toBytes(transactionData);

			default:
				throw new TransformationException("Unsupported transaction type");
		}
	}

	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		switch (transactionData.getType()) {
			case GENESIS:
				return GenesisTransactionTransformer.toJSON(transactionData);

			case PAYMENT:
				return PaymentTransactionTransformer.toJSON(transactionData);

			case REGISTER_NAME:
				return RegisterNameTransactionTransformer.toJSON(transactionData);

			case UPDATE_NAME:
				return UpdateNameTransactionTransformer.toJSON(transactionData);

			case CREATE_POLL:
				return CreatePollTransactionTransformer.toJSON(transactionData);

			case VOTE_ON_POLL:
				return VoteOnPollTransactionTransformer.toJSON(transactionData);

			case ISSUE_ASSET:
				return IssueAssetTransactionTransformer.toJSON(transactionData);

			case TRANSFER_ASSET:
				return TransferAssetTransactionTransformer.toJSON(transactionData);

			case CREATE_ASSET_ORDER:
				return CreateOrderTransactionTransformer.toJSON(transactionData);

			case CANCEL_ASSET_ORDER:
				return CancelOrderTransactionTransformer.toJSON(transactionData);

			case MULTIPAYMENT:
				return MultiPaymentTransactionTransformer.toJSON(transactionData);

			case MESSAGE:
				return MessageTransactionTransformer.toJSON(transactionData);

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
