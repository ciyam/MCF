package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.RegisterNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class RegisterNameTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String owner = account.getAddress();
		String name = "test name";
		if (!wantValid)
			name += " " + random.nextInt(1_000_000);

		String data = "{ \"key\": \"value\" }";

		return new RegisterNameTransactionData(generateBase(account), owner, name, data);
	}

}
