package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.AccountFlagsTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AccountFlagsTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int andMask = -1;
		final int orMask = 0;
		final int xorMask = 0;

		return new AccountFlagsTransactionData(generateBase(account), account.getAddress(), andMask, orMask, xorMask);
	}

}
