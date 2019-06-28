package controller;

import api.ApiService;
import qora.block.BlockChain;
import repository.DataException;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;
import settings.Settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Controller {

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);

	public static final String connectionUrl = "jdbc:hsqldb:file:db/blockchain;create=true";

	public static final long startTime = System.currentTimeMillis();
	private static final Object shutdownLock = new Object();
	private static boolean isStopping = false;

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

		try {
			BlockChain.validate();
		} catch (DataException e) {
			LOGGER.error("Couldn't validate repository", e);
			System.exit(2);
		}

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
