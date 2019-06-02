package org.qora.test.common;

import org.qora.account.PrivateKeyAccount;
import org.qora.repository.Repository;
import org.qora.utils.Base58;

public class TestAccount extends PrivateKeyAccount {

	public final String accountName;

	public TestAccount(Repository repository, String accountName, byte[] privateKey) {
		super(repository, privateKey);

		this.accountName = accountName;
	}

	public TestAccount(Repository repository, String accountName, String privateKey) {
		this(repository, accountName, Base58.decode(privateKey));
	}

}
