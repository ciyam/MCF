package org.qora.test.common.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.PaymentData;
import org.qora.data.transaction.MultiPaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class MultiPaymentTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		String recipient = account.getAddress();
		final long assetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(123L);

		List<PaymentData> payments = new ArrayList<>();
		payments.add(new PaymentData(recipient, assetId, amount));

		return new MultiPaymentTransactionData(generateBase(account), payments);
	}

}
