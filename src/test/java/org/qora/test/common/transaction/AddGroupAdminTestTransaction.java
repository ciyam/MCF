package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AddGroupAdminTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int groupId = 1;
		String member = account.getAddress();

		return new AddGroupAdminTransactionData(generateBase(account), groupId, member);
	}

}
