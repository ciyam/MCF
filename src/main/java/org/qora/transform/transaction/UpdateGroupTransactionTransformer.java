package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;

public class UpdateGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int NEW_OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NEW_DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_IS_OPEN_LENGTH = BOOLEAN_LENGTH;
	private static final int NEW_APPROVAL_THRESHOLD_LENGTH = BYTE_LENGTH;
	private static final int NEW_MINIMUM_BLOCK_DELAY_LENGTH = INT_LENGTH;
	private static final int NEW_MAXIMUM_BLOCK_DELAY_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = GROUPID_LENGTH + NEW_OWNER_LENGTH + NEW_DESCRIPTION_SIZE_LENGTH + NEW_IS_OPEN_LENGTH
			+ NEW_APPROVAL_THRESHOLD_LENGTH + NEW_MINIMUM_BLOCK_DELAY_LENGTH + NEW_MAXIMUM_BLOCK_DELAY_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.UPDATE_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("group's new owner", TransformationType.ADDRESS);
		layout.add("group's new description length", TransformationType.INT);
		layout.add("group's new description", TransformationType.STRING);
		layout.add("is group \"open\"?", TransformationType.BOOLEAN);
		layout.add("new group transaction approval threshold", TransformationType.BYTE);
		layout.add("new group approval minimum block delay", TransformationType.INT);
		layout.add("new group approval maximum block delay", TransformationType.INT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String newOwner = Serialization.deserializeAddress(byteBuffer);

		String newDescription = Serialization.deserializeSizedString(byteBuffer, Group.MAX_DESCRIPTION_SIZE);

		boolean newIsOpen = byteBuffer.get() != 0;

		ApprovalThreshold newApprovalThreshold = ApprovalThreshold.valueOf(byteBuffer.get());

		int newMinBlockDelay = byteBuffer.getInt();

		int newMaxBlockDelay = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new UpdateGroupTransactionData(timestamp, txGroupId, reference, ownerPublicKey, groupId, newOwner, newDescription, newIsOpen,
				newApprovalThreshold, newMinBlockDelay, newMaxBlockDelay, fee, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(updateGroupTransactionData.getNewDescription());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Ints.toByteArray(updateGroupTransactionData.getGroupId()));

			Serialization.serializeAddress(bytes, updateGroupTransactionData.getNewOwner());

			Serialization.serializeSizedString(bytes, updateGroupTransactionData.getNewDescription());

			bytes.write((byte) (updateGroupTransactionData.getNewIsOpen() ? 1 : 0));

			bytes.write((byte) updateGroupTransactionData.getNewApprovalThreshold().value);

			bytes.write(Ints.toByteArray(updateGroupTransactionData.getNewMinimumBlockDelay()));

			bytes.write(Ints.toByteArray(updateGroupTransactionData.getNewMaximumBlockDelay()));

			Serialization.serializeBigDecimal(bytes, updateGroupTransactionData.getFee());

			if (updateGroupTransactionData.getSignature() != null)
				bytes.write(updateGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
