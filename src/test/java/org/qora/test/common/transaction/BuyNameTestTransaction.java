package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.BuyNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class BuyNameTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String name = "test name";
		if (!wantValid)
			name += " " + random.nextInt(1_000_000);

		BigDecimal amount = BigDecimal.valueOf(123);
		String seller = account.getAddress();

		return new BuyNameTransactionData(generateBase(account), name, amount, seller);
	}

}
