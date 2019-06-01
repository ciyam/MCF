package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.Repository;

public class PaymentTransaction extends org.qora.test.common.transaction.Transaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) {
		return new PaymentTransactionData(generateBase(account), account.getAddress(), BigDecimal.valueOf(123L));
	}

}
