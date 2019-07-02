package org.qora.test.common.transaction;

import java.util.Random;

import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;

public abstract class TestTransaction {

	protected static final Random random = new Random();

	protected static BaseTransactionData generateBase(PrivateKeyAccount account) throws DataException {
		byte[] lastReference = account.getUnconfirmedLastReference();
		if (lastReference == null)
			lastReference = account.getLastReference();

		return new BaseTransactionData(System.currentTimeMillis(), Group.NO_GROUP, lastReference, account.getPublicKey(), BlockChain.getInstance().getUnitFee(), null);
	}

}
