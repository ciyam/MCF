package org.qora.test.common.transaction;

import java.math.BigDecimal;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.Repository;
import org.qora.utils.NTP;

public class PaymentTransaction  {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) {
		return new PaymentTransactionData(NTP.getTime(), Group.NO_GROUP, new byte[32], account.getPublicKey(), account.getAddress(), BigDecimal.valueOf(123L), BigDecimal.ONE);
	}

}
