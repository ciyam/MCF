package org.qora.test.common;

import org.qora.account.PrivateKeyAccount;
import org.qora.utils.Base58;

public class TestAccount extends PrivateKeyAccount {
	public TestAccount(String privateKey) {
		super(null, Base58.decode(privateKey));
	}
}
