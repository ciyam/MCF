package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.transaction.CreatePollTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.voting.PollOptionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;
import org.qora.voting.Poll;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class CreatePollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int OPTIONS_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH
			+ OPTIONS_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_POLL.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("poll creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("poll name length", TransformationType.INT);
		layout.add("poll name", TransformationType.STRING);
		layout.add("poll description length", TransformationType.INT);
		layout.add("poll description", TransformationType.STRING);
		layout.add("number of options", TransformationType.INT);
		layout.add("* poll option length", TransformationType.INT);
		layout.add("* poll option", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_DESCRIPTION_SIZE);

		int optionsCount = byteBuffer.getInt();
		if (optionsCount < 1 || optionsCount > Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid number of options for CreatePollTransaction");

		List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();
		for (int optionIndex = 0; optionIndex < optionsCount; ++optionIndex) {
			String optionName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

			pollOptions.add(new PollOptionData(optionName));

			// V1 only: voter count also present
			if (timestamp < BlockChain.getInstance().getQoraV2Timestamp()) {
				int voterCount = byteBuffer.getInt();
				if (voterCount != 0)
					throw new TransformationException("Unexpected voter count in byte data for CreatePollTransaction");
			}
		}

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CreatePollTransactionData(creatorPublicKey, owner, pollName, description, pollOptions, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(createPollTransactionData.getPollName())
				+ Utf8.encodedLength(createPollTransactionData.getDescription());

		// Add lengths for each poll options
		for (PollOptionData pollOptionData : createPollTransactionData.getPollOptions()) {
			// option-string-length, option-string
			dataLength += INT_LENGTH + Utf8.encodedLength(pollOptionData.getOptionName());

			if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
				// v1 only: voter-count (should always be zero)
				dataLength += INT_LENGTH;
		}

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(createPollTransactionData.getType().value));

			bytes.write(Longs.toByteArray(createPollTransactionData.getTimestamp()));

			bytes.write(createPollTransactionData.getReference());

			bytes.write(createPollTransactionData.getCreatorPublicKey());

			Serialization.serializeAddress(bytes, createPollTransactionData.getOwner());
			Serialization.serializeSizedString(bytes, createPollTransactionData.getPollName());
			Serialization.serializeSizedString(bytes, createPollTransactionData.getDescription());

			List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
			bytes.write(Ints.toByteArray(pollOptions.size()));

			for (PollOptionData pollOptionData : pollOptions) {
				Serialization.serializeSizedString(bytes, pollOptionData.getOptionName());

				if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp()) {
					// In v1, CreatePollTransaction uses Poll.toBytes which serializes voters too.
					// Zero voters as this is a new poll.
					bytes.write(Ints.toByteArray(0));
				}
			}

			Serialization.serializeBigDecimal(bytes, createPollTransactionData.getFee());

			if (createPollTransactionData.getSignature() != null)
				bytes.write(createPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification have transaction type set to REGISTER_NAME_TRANSACTION so we need to test for v1-ness and adjust the bytes
	 * accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);

		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return bytes;

		// Special v1 version

		// Replace transaction type with incorrect Register Name value
		System.arraycopy(Ints.toByteArray(TransactionType.REGISTER_NAME.value), 0, bytes, 0, INT_LENGTH);

		return bytes;
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

			byte[] creatorPublicKey = createPollTransactionData.getCreatorPublicKey();

			json.put("creator", PublicKeyAccount.getAddress(creatorPublicKey));
			json.put("creatorPublicKey", HashCode.fromBytes(creatorPublicKey).toString());

			json.put("owner", createPollTransactionData.getOwner());
			json.put("name", createPollTransactionData.getPollName());
			json.put("description", createPollTransactionData.getDescription());

			JSONArray options = new JSONArray();
			for (PollOptionData optionData : createPollTransactionData.getPollOptions())
				options.add(optionData.getOptionName());

			json.put("options", options);
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
