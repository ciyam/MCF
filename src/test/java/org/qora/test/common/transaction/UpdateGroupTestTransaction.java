package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class UpdateGroupTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int groupId = 1;
		String newOwner = account.getAddress();
		String newDescription = "updated random test group";
		final boolean newIsOpen = false;
		ApprovalThreshold newApprovalThreshold = ApprovalThreshold.PCT20;
		final int newMinimumBlockDelay = 10;
		final int newMaximumBlockDelay = 60;

		return new UpdateGroupTransactionData(generateBase(account), groupId, newOwner, newDescription, newIsOpen, newApprovalThreshold, newMinimumBlockDelay, newMaximumBlockDelay);
	}

}
