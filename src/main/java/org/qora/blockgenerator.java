package org.qora;
import java.security.SecureRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.repository.DataException;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.utils.Base58;

public class blockgenerator {

	private static final Logger LOGGER = LogManager.getLogger(blockgenerator.class);
	public static final String connectionUrl = "jdbc:hsqldb:file:db/blockchain;create=true";

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("usage: blockgenerator private-key-base58 | 'RANDOM'");
			System.err.println("example: blockgenerator 7Vg53HrETZZuVySMPWJnVwQESS3dV8jCXPL5GDHMCeKS");
			System.exit(1);
		}

		byte[] privateKey;

		if (args[0].equalsIgnoreCase("RANDOM")) {
			privateKey = new byte[32];
			new SecureRandom().nextBytes(privateKey);
		} else {
			privateKey = Base58.decode(args[0]);
		}

		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			LOGGER.error("Couldn't connect to repository", e);
			System.exit(2);
		}

		try {
			BlockChain.validate();
		} catch (DataException e) {
			LOGGER.error("Couldn't validate repository", e);
			System.exit(2);
		}

		BlockGenerator blockGenerator = new BlockGenerator();
		blockGenerator.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				blockGenerator.shutdown();

				try {
					blockGenerator.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				try {
					RepositoryManager.closeRepositoryFactory();
				} catch (DataException e) {
					e.printStackTrace();
				}
			}
		});
	}

}
