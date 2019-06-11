package org.qora.test.common.transaction;

import java.math.BigDecimal;
import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.DeployAtTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class DeployAtTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String name = "test AT " + random.nextInt(1_000_000);
		String description = "random test AT";
		String atType = "random AT type";
		String tags = "random AT tags";
		byte[] creationBytes = new byte[1024];
		random.nextBytes(creationBytes);
		BigDecimal amount = BigDecimal.valueOf(123);
		final long assetId = Asset.QORA;

		return new DeployAtTransactionData(generateBase(account), name, description, atType, tags, creationBytes, amount, assetId);
	}

}
