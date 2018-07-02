package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.CreatePollTransactionData;
import data.transaction.TransactionData;
import data.voting.PollOptionData;
import qora.account.PublicKeyAccount;
import qora.voting.Poll;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class CreatePollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int OPTIONS_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH
			+ OPTIONS_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for CreatePollTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeRecipient(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);
		String description = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_DESCRIPTION_SIZE);

		// Make sure there are enough bytes left for poll options
		if (byteBuffer.remaining() < OPTIONS_SIZE_LENGTH)
			throw new TransformationException("Byte data too short for CreatePollTransaction");

		int optionsCount = byteBuffer.getInt();
		if (optionsCount < 1 || optionsCount > Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid number of options for CreatePollTransaction");

		List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();
		for (int optionIndex = 0; optionIndex < optionsCount; ++optionIndex) {
			String optionName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

			pollOptions.add(new PollOptionData(optionName));
		}

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for CreatePollTransaction");

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
		for (PollOptionData pollOptionData : createPollTransactionData.getPollOptions())
			dataLength += INT_LENGTH + Utf8.encodedLength(pollOptionData.getOptionName());

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
			bytes.write(Base58.decode(createPollTransactionData.getOwner()));
			Serialization.serializeSizedString(bytes, createPollTransactionData.getPollName());
			Serialization.serializeSizedString(bytes, createPollTransactionData.getDescription());

			List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
			bytes.write(Ints.toByteArray(pollOptions.size()));

			for (PollOptionData pollOptionData : pollOptions)
				Serialization.serializeSizedString(bytes, pollOptionData.getOptionName());

			Serialization.serializeBigDecimal(bytes, createPollTransactionData.getFee());

			if (createPollTransactionData.getSignature() != null)
				bytes.write(createPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
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
