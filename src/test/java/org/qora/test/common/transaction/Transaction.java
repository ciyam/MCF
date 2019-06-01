package org.qora.test.common.transaction;

import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.group.Group;
import org.qora.utils.NTP;

public abstract class Transaction {

	protected static BaseTransactionData generateBase(PrivateKeyAccount account) {
		return new BaseTransactionData(NTP.getTime(), Group.NO_GROUP, new byte[32], account.getPublicKey(), BlockChain.getInstance().getUnitFee(), null);
	}

}
