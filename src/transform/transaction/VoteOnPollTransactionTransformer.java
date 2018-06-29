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
import data.transaction.VoteOnPollTransactionData;
import qora.account.PublicKeyAccount;
import qora.voting.Poll;
import transform.TransformationException;
import utils.Serialization;

public class VoteOnPollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int VOTER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int OPTION_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + VOTER_LENGTH + NAME_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for VoteOnPollTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] voterPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

		// Make sure there are enough bytes left for poll options
		if (byteBuffer.remaining() < OPTION_LENGTH)
			throw new TransformationException("Byte data too short for VoteOnPollTransaction");

		int optionIndex = byteBuffer.getInt();
		if (optionIndex < 0 || optionIndex >= Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid option number for VoteOnPollTransaction");

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for VoteOnPollTransaction");

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new VoteOnPollTransactionData(voterPublicKey, pollName, optionIndex, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + voteOnPollTransactionData.getPollName().length();

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(voteOnPollTransactionData.getType().value));
			bytes.write(Longs.toByteArray(voteOnPollTransactionData.getTimestamp()));
			bytes.write(voteOnPollTransactionData.getReference());

			bytes.write(voteOnPollTransactionData.getVoterPublicKey());
			Serialization.serializeSizedString(bytes, voteOnPollTransactionData.getPollName());
			bytes.write(voteOnPollTransactionData.getOptionIndex());

			Serialization.serializeBigDecimal(bytes, voteOnPollTransactionData.getFee());

			if (voteOnPollTransactionData.getSignature() != null)
				bytes.write(voteOnPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

			byte[] voterPublicKey = voteOnPollTransactionData.getVoterPublicKey();

			json.put("voter", PublicKeyAccount.getAddress(voterPublicKey));
			json.put("voterPublicKey", HashCode.fromBytes(voterPublicKey).toString());

			json.put("name", voteOnPollTransactionData.getPollName());
			json.put("optionIndex", voteOnPollTransactionData.getOptionIndex());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
