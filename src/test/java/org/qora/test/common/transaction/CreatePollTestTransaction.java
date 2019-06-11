package org.qora.test.common.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.CreatePollTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.voting.PollOptionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CreatePollTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String owner = account.getAddress();
		String pollName = "test poll " + random.nextInt(1_000_000);
		String description = "Not ready reading drive A";

		List<PollOptionData> pollOptions = new ArrayList<>();
		pollOptions.add(new PollOptionData("Abort"));
		pollOptions.add(new PollOptionData("Retry"));
		pollOptions.add(new PollOptionData("Fail"));

		return new CreatePollTransactionData(generateBase(account), owner, pollName, description, pollOptions);
	}

}
