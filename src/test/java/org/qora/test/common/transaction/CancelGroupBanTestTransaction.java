package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CancelGroupBanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CancelGroupBanTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int groupId = 1;
		String member = account.getAddress();

		return new CancelGroupBanTransactionData(generateBase(account), groupId, member);
	}

}
