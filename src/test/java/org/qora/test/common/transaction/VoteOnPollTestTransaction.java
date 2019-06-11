package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.VoteOnPollTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class VoteOnPollTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String pollName = "test poll " + random.nextInt(1_000_000);
		final int optionIndex = random.nextInt(3);

		return new VoteOnPollTransactionData(generateBase(account), pollName, optionIndex);
	}

}
