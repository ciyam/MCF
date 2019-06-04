package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;

public class CreateGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int IS_OPEN_LENGTH = BOOLEAN_LENGTH;
	private static final int APPROVAL_THRESHOLD_LENGTH = BYTE_LENGTH;
	private static final int BLOCK_DELAY_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + IS_OPEN_LENGTH
			+ APPROVAL_THRESHOLD_LENGTH + BLOCK_DELAY_LENGTH + BLOCK_DELAY_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("group's name length", TransformationType.INT);
		layout.add("group's name", TransformationType.STRING);
		layout.add("group's description length", TransformationType.INT);
		layout.add("group's description", TransformationType.STRING);
		layout.add("is group \"open\"?", TransformationType.BOOLEAN);
		layout.add("group transaction approval threshold", TransformationType.BYTE);
		layout.add("minimum block delay for transaction approvals", TransformationType.INT);
		layout.add("maximum block delay for transaction approvals", TransformationType.INT);
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

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Group.MAX_DESCRIPTION_SIZE);

		boolean isOpen = byteBuffer.get() != 0;

		ApprovalThreshold approvalThreshold = ApprovalThreshold.valueOf(byteBuffer.get());

		int minBlockDelay = byteBuffer.getInt();

		int maxBlockDelay = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new CreateGroupTransactionData(baseTransactionData, owner, groupName, description, isOpen, approvalThreshold, minBlockDelay, maxBlockDelay);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(createGroupTransactionData.getGroupName())
				+ Utf8.encodedLength(createGroupTransactionData.getDescription());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, createGroupTransactionData.getOwner());

			Serialization.serializeSizedString(bytes, createGroupTransactionData.getGroupName());

			Serialization.serializeSizedString(bytes, createGroupTransactionData.getDescription());

			bytes.write((byte) (createGroupTransactionData.getIsOpen() ? 1 : 0));

			bytes.write((byte) createGroupTransactionData.getApprovalThreshold().value);

			bytes.write(Ints.toByteArray(createGroupTransactionData.getMinimumBlockDelay()));

			bytes.write(Ints.toByteArray(createGroupTransactionData.getMaximumBlockDelay()));

			Serialization.serializeBigDecimal(bytes, createGroupTransactionData.getFee());

			if (createGroupTransactionData.getSignature() != null)
				bytes.write(createGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
