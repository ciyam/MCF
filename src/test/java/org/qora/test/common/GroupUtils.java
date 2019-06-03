package org.qora.test.common;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.Transaction.ApprovalStatus;

public class GroupUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final BigDecimal fee = BigDecimal.ONE.setScale(8);

	public static int createGroup(Repository repository, String creatorAccountName, String groupName, boolean isOpen, ApprovalThreshold approvalThreshold,
				int minimumBlockDelay, int maximumBlockDelay) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, creatorAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;
		String groupDescription = groupName + " (test group)";

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, account.getPublicKey(), GroupUtils.fee, null);
		TransactionData transactionData = new CreateGroupTransactionData(baseTransactionData, account.getAddress(), groupName, groupDescription, isOpen, approvalThreshold, minimumBlockDelay, maximumBlockDelay);

		TransactionUtils.signAndForge(repository, transactionData, account);

		return repository.getGroupRepository().fromGroupName(groupName).getGroupId();
	}

	public static void joinGroup(Repository repository, String joinerAccountName, int groupId) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, joinerAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, account.getPublicKey(), GroupUtils.fee, null);
		TransactionData transactionData = new JoinGroupTransactionData(baseTransactionData, groupId);

		TransactionUtils.signAndForge(repository, transactionData, account);
	}

	public static void approveTransaction(Repository repository, String accountName, byte[] pendingSignature, boolean decision) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, accountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, account.getPublicKey(), GroupUtils.fee, null);
		TransactionData transactionData = new GroupApprovalTransactionData(baseTransactionData, pendingSignature, decision);

		TransactionUtils.signAndForge(repository, transactionData, account);
	}

	public static ApprovalStatus getApprovalStatus(Repository repository, byte[] signature) throws DataException {
		return repository.getTransactionRepository().fromSignature(signature).getApprovalStatus();
	}

	public static Integer getApprovalHeight(Repository repository, byte[] signature) throws DataException {
		return repository.getTransactionRepository().fromSignature(signature).getApprovalHeight();
	}

}
