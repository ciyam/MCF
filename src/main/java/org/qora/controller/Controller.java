package org.qora.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.api.ApiService;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.repository.DataException;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;
import org.qora.utils.Base58;

public class Controller {

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);

	public static final String connectionUrl = "jdbc:hsqldb:file:db/blockchain;create=true";

	public static final long startTime = System.currentTimeMillis();
	private static final Object shutdownLock = new Object();
	private static boolean isStopping = false;

	private static BlockGenerator blockGenerator;

	public static void main(String args[]) {
		LOGGER.info("Starting up...");

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		LOGGER.info("Starting repository");
		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			LOGGER.error("Unable to start repository", e);
			System.exit(1);
		}

		LOGGER.info("Validating blockchain");
		try {
			BlockChain.validate();
		} catch (DataException e) {
			LOGGER.error("Couldn't validate blockchain", e);
			System.exit(2);
		}

		LOGGER.info("Starting block generator");
		byte[] privateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		blockGenerator = new BlockGenerator(privateKey);
		blockGenerator.start();

		LOGGER.info("Starting API");
		try {
			ApiService apiService = ApiService.getInstance();
			apiService.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start API", e);
			System.exit(1);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Controller.shutdown();
			}
		});
	}

	public static void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down API");
				ApiService.getInstance().stop();

				LOGGER.info("Shutting down block generator");
				blockGenerator.shutdown();
				try {
					blockGenerator.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				try {
					LOGGER.info("Shutting down repository");
					RepositoryManager.closeRepositoryFactory();
				} catch (DataException e) {
					e.printStackTrace();
				}

				LOGGER.info("Shutdown complete!");
			}
		}
	}

}
