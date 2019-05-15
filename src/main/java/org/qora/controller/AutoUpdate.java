package org.qora.controller;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.ApplyUpdate;
import org.qora.api.ApiRequest;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.gui.SysTray;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.transaction.ArbitraryTransaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.Transformer;
import org.qora.utils.NTP;

import com.google.common.hash.HashCode;

public class AutoUpdate extends Thread {

	public static final String JAR_FILENAME = "MCF-core.jar";
	public static final String NEW_JAR_FILENAME = "new-" + JAR_FILENAME;

	private static final Logger LOGGER = LogManager.getLogger(AutoUpdate.class);
	private static final long CHECK_INTERVAL = 5 * 60 * 1000; // ms

	private static final int DEV_GROUP_ID = 1;
	private static final int UPDATE_SERVICE = 1;
	private static final List<TransactionType> ARBITRARY_TX_TYPE = Arrays.asList(TransactionType.ARBITRARY);

	private static final int GIT_COMMIT_HASH_LENGTH = 20; // SHA-1
	private static final int EXPECTED_DATA_LENGTH = Transformer.TIMESTAMP_LENGTH + GIT_COMMIT_HASH_LENGTH + Transformer.SHA256_LENGTH;

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

				// TODO: check arbitrary data length (pre-fetch) matches build timestamp (8) + git commit length (20) + sha256 hash length (32) = 60 bytes

				byte[] data = arbitraryTransaction.fetchData();
				if (data.length != EXPECTED_DATA_LENGTH) {
					LOGGER.debug(String.format("Arbitrary data length %d doesn't match %d", data.length, EXPECTED_DATA_LENGTH));
					continue;
				}

				ByteBuffer byteBuffer = ByteBuffer.wrap(data);

				long updateTimestamp = byteBuffer.getLong();
				if (updateTimestamp <= buildTimestamp)
					continue; // update is the same, or older, than current code

				byte[] commitHash = new byte[GIT_COMMIT_HASH_LENGTH];
				byteBuffer.get(commitHash);

				byte[] downloadHash = new byte[Transformer.SHA256_LENGTH];
				byteBuffer.get(downloadHash);

				LOGGER.info(String.format("Update's git commit hash: %s", HashCode.fromBytes(commitHash).toString()));

				String[] autoUpdateRepos = Settings.getInstance().getAutoUpdateRepos();
				for (String repo : autoUpdateRepos)
					if (attemptUpdate(commitHash, downloadHash, repo)) {
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
		this.interrupt();
	}

	private static boolean attemptUpdate(byte[] commitHash, byte[] downloadHash, String repoBaseUri) {
		LOGGER.info(String.format("Fetching update from %s", repoBaseUri));
		InputStream in = ApiRequest.fetchStream(repoBaseUri + "/raw/" + HashCode.fromBytes(commitHash).toString() + "/" + JAR_FILENAME);
		if (in == null) {
			LOGGER.warn(String.format("Failed to fetch update from %s", repoBaseUri));
			return false; // failed - try another repo
		}

		Path newJar = Paths.get(NEW_JAR_FILENAME);
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

			// Save input stream into new JAR
			LOGGER.debug(String.format("Saving update from %s into %s", repoBaseUri, newJar.toString()));

			OutputStream out = Files.newOutputStream(newJar);
			byte[] buffer = new byte[1024 * 1024];
			do {
				int nread = in.read(buffer);
				if (nread == -1)
					break;

				sha256.update(buffer, 0, nread);
				out.write(buffer, 0, nread);
			} while (true);

			// Check hash
			byte[] hash = sha256.digest();
			if (!Arrays.equals(downloadHash, hash)) {
				LOGGER.warn(String.format("Downloaded JAR's hash %s doesn't match %s", HashCode.fromBytes(hash).toString(), HashCode.fromBytes(downloadHash).toString()));

				try {
					Files.deleteIfExists(newJar);
				} catch (IOException de) {
					LOGGER.warn(String.format("Failed to delete download: %s", de.getMessage()));
				}

				return false;
			}
		} catch (IOException e) {
			LOGGER.warn(String.format("Failed to save update from %s into %s", repoBaseUri, newJar.toString()));

			try {
				Files.deleteIfExists(newJar);
			} catch (IOException de) {
				LOGGER.warn(String.format("Failed to delete partial download: %s", de.getMessage()));
			}

			return false; // failed - try another repo
		} catch (NoSuchAlgorithmException e) {
			return true; // not repo's fault
		}

		// Call ApplyUpdate to end this process (unlocking current JAR so it can be replaced)
		String javaHome = System.getProperty("java.home");
		LOGGER.debug(String.format("Java home: %s", javaHome));

		Path javaBinary = Paths.get(javaHome, "bin", "java");
		LOGGER.debug(String.format("Java binary: %s", javaBinary));

		try {
			List<String> javaCmd = Arrays.asList(javaBinary.toString(), "-cp", NEW_JAR_FILENAME, ApplyUpdate.class.getCanonicalName());
			LOGGER.info(String.format("Applying update with: %s", String.join(" ", javaCmd)));

			SysTray.getInstance().showMessage("Auto Update", "Applying automatic update and restarting...", MessageType.INFO);

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
