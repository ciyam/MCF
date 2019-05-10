package org.qora.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.ApplyUpdate;
import org.qora.api.ApiRequest;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.transaction.ArbitraryTransaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.utils.NTP;

import com.google.common.hash.HashCode;

public class AutoUpdate extends Thread {

	public static final String JAR_FILENAME = "MCF-core.jar";
	public static final String NEW_JAR_FILENAME = "new-" + JAR_FILENAME;

	private static final Logger LOGGER = LogManager.getLogger(AutoUpdate.class);
	private static final long CHECK_INTERVAL = 5 * 1000; // ms

	private static final int DEV_GROUP_ID = 1;
	private static final int UPDATE_SERVICE = 1;
	private static final List<TransactionType> ARBITRARY_TX_TYPE = Arrays.asList(TransactionType.ARBITRARY);

	private static AutoUpdate instance;

	private boolean isStopping = false;

	private AutoUpdate() {
	}

	public static AutoUpdate getInstance() {
		if (instance == null)
			instance = new AutoUpdate();

		return instance;
	}

	public void run() {
		long buildTimestamp = Controller.getInstance().getBuildTimestamp() * 1000L;
		boolean attemptedUpdate = false;

		while (!isStopping) {
			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				return;
			}

			// Try to clean up any leftover downloads (but if we are/have attempted update then don't delete new JAR)
			if (!attemptedUpdate)
				try {
					Path newJar = Paths.get(NEW_JAR_FILENAME);
					Files.deleteIfExists(newJar);
				} catch (IOException de) {
					// Whatever
				}

			// Look for "update" tx which is arbitrary tx in dev-group with service 1 and timestamp later than buildTimestamp
			try (final Repository repository = RepositoryManager.getRepository()) {
				List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, DEV_GROUP_ID, ARBITRARY_TX_TYPE, UPDATE_SERVICE, null, ConfirmationStatus.CONFIRMED, 1, null, true);
				if (signatures == null || signatures.isEmpty())
					continue;

				TransactionData transactionData = repository.getTransactionRepository().fromSignature(signatures.get(0));
				if (transactionData == null || !(transactionData instanceof ArbitraryTransactionData))
					continue;

				// Transaction needs to be newer than this build
				if (transactionData.getTimestamp() <= buildTimestamp)
					continue;

				ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
				if (!arbitraryTransaction.isDataLocal())
					continue; // We can't access data

				// TODO: check arbitrary data length (pre-fetch) matches git commit length (20) + sha256 hash length (32) = 52 bytes

				byte[] commitHash = arbitraryTransaction.fetchData();
				LOGGER.info(String.format("Update's git commit hash: %s", HashCode.fromBytes(commitHash).toString()));

				String[] autoUpdateRepos = Settings.getInstance().getAutoUpdateRepos();
				for (String repo : autoUpdateRepos)
					if (attemptUpdate(commitHash, repo)) {
						// Consider ourselves updated so don't re-re-re-download
						buildTimestamp = NTP.getTime();
						attemptedUpdate = true;
						break;
					}
			} catch (DataException e) {
				LOGGER.warn("Repository issue to find updates", e);
				// Keep going I guess...
			}
		}

		LOGGER.info("Stopping auto-update service");
	}

	public void shutdown() {
		isStopping = true;
	}

	private static boolean attemptUpdate(byte[] commitHash, String repoBaseUri) {
		LOGGER.info(String.format("Fetching update from %s", repoBaseUri));
		InputStream in = ApiRequest.fetchStream(repoBaseUri + "/raw/" + HashCode.fromBytes(commitHash).toString() + "/" + JAR_FILENAME);
		if (in == null) {
			LOGGER.warn(String.format("Failed to fetch update from %s", repoBaseUri));
			return false; // failed - try another repo
		}

		Path newJar = Paths.get(NEW_JAR_FILENAME);
		try {
			// Save input stream into new JAR
			LOGGER.debug(String.format("Saving update from %s into %s", repoBaseUri, newJar.toString()));
			Files.copy(in, newJar, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn(String.format("Failed to save update from %s into %s", repoBaseUri, newJar.toString()));

			try {
				Files.deleteIfExists(newJar);
			} catch (IOException de) {
				LOGGER.warn(String.format("Failed to delete partial download: %s", de.getMessage()));
			}

			return false; // failed - try another repo
		}

		// Call ApplyUpdate to end this process (unlocking current JAR so it can be replaced)
		String javaHome = System.getProperty("java.home");
		LOGGER.debug(String.format("Java home: %s", javaHome));

		Path javaBinary = Paths.get(javaHome, "bin", "java");
		LOGGER.debug(String.format("Java binary: %s", javaBinary));

		try {
			List<String> javaCmd = Arrays.asList(javaBinary.toString(), "-cp", NEW_JAR_FILENAME, ApplyUpdate.class.getCanonicalName());
			LOGGER.info(String.format("Applying update with: %s", String.join(" ", javaCmd)));

			new ProcessBuilder(javaCmd).start();

			return true; // applying update OK
		} catch (IOException e) {
			LOGGER.error(String.format("Failed to apply update: %s", e.getMessage()));

			try {
				Files.deleteIfExists(newJar);
			} catch (IOException de) {
				LOGGER.warn(String.format("Failed to delete update download: %s", de.getMessage()));
			}

			return true; // repo was okay, even if applying update failed
		}
	}

}
