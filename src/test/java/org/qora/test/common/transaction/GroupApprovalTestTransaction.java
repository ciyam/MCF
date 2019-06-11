package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class GroupApprovalTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		byte[] pendingSignature = new byte[64];
		random.nextBytes(pendingSignature);
		final boolean approval = true;

		return new GroupApprovalTransactionData(generateBase(account), pendingSignature, approval);
	}

}
