package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CreateGroupTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String owner = account.getAddress();
		String groupName = "test group " + random.nextInt(1_000_000);
		String description = "random test group";
		final boolean isOpen = false;
		ApprovalThreshold approvalThreshold = ApprovalThreshold.PCT40;
		final int minimumBlockDelay = 5;
		final int maximumBlockDelay = 20;

		return new CreateGroupTransactionData(generateBase(account), owner, groupName, description, isOpen, approvalThreshold, minimumBlockDelay, maximumBlockDelay);
	}

}
