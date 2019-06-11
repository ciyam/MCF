package org.qora.test.common.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.PaymentData;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.ArbitraryTransactionData.DataType;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class ArbitraryTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int version = 4;
		final int service = 123;

		byte[] data = new byte[1024];
		random.nextBytes(data);

		DataType dataType = DataType.RAW_DATA;

		String recipient = account.getAddress();
		final long assetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(123L);

		List<PaymentData> payments = new ArrayList<>();
		payments.add(new PaymentData(recipient, assetId, amount));

		return new ArbitraryTransactionData(generateBase(account), version, service, data, dataType, payments);
	}

}
