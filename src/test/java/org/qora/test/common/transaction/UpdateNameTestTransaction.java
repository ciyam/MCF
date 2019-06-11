package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class UpdateNameTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String newOwner = account.getAddress();
		String name = "test name";
		if (!wantValid)
			name += " " + random.nextInt(1_000_000);

		String newData = "{ \"key\": \"updated value\" }";

		return new UpdateNameTransactionData(generateBase(account), newOwner, name, newData);
	}

}
