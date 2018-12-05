package controller;

import api.ApiService;
import repository.DataException;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Controller {

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);

	private static final String connectionUrl = "jdbc:hsqldb:file:db/test;create=true";
	private static final Object shutdownLock = new Object();
	private static boolean isStopping = false;

	public static void main(String args[]) throws DataException {
		LOGGER.info("Starting up...");

		LOGGER.info("Starting repository");
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);

		LOGGER.info("Starting API");
		ApiService apiService = ApiService.getInstance();
		apiService.start();

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
