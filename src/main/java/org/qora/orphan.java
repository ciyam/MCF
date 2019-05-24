package org.qora;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.qora.block.BlockChain;
import org.qora.controller.Controller;
import org.qora.repository.DataException;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;

public class orphan {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: orphan <new-blockchain-tip-height>");
			System.exit(1);
		}

		int targetHeight = Integer.parseInt(args[0]);

		Security.insertProviderAt(new BouncyCastleProvider(), 0);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(Controller.getRepositoryUrl());
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			System.err.println("Couldn't connect to repository: " + e.getMessage());
			System.exit(2);
		}

		try {
			BlockChain.validate();
		} catch (DataException e) {
			System.err.println("Couldn't validate repository: " + e.getMessage());
			System.exit(2);
		}

		try {
			BlockChain.orphan(targetHeight);
		} catch (DataException e) {
			e.printStackTrace();
		}

		try {
			RepositoryManager.closeRepositoryFactory();
		} catch (DataException e) {
			e.printStackTrace();
		}
	}

}
