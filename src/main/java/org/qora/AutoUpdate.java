package org.qora;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.api.ApiRequest;
import org.qora.api.model.NodeInfo;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.settings.Settings;
import org.qora.utils.Base58;

import com.google.common.hash.HashCode;

public class AutoUpdate {

	static {
		// This static block will be called before others if using AutoUpdate.main()

		// Log into different files for auto-update
		System.setProperty("log4j2.filenameTemplate", "log-auto-update.txt");

		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static final Logger LOGGER = LogManager.getLogger(AutoUpdate.class);
	private static final Logger AU_LOGGER = LogManager.getLogger("auto-update");

	private static final String JAR_FILENAME = "MCF-core.jar";
	private static final String NODE_EXE = "MCF.exe";
	private static final String SERVICE_NAME = "MCF auto-update.exe";

	private static final long CHECK_INTERVAL = 1 * 1000; // ms
	private static final int MAX_ATTEMPTS = 5;

	private static final Map<String, String> ARBITRARY_PARAMS = new HashMap<>();
	static {
		ARBITRARY_PARAMS.put("txGroupID", "1"); // dev group
		ARBITRARY_PARAMS.put("service", "1"); // "update" service
		ARBITRARY_PARAMS.put("confirmationStatus", "CONFIRMED");
		ARBITRARY_PARAMS.put("limit", "1");
		ARBITRARY_PARAMS.put("reverse", "true");
	}

	private static volatile boolean stopRequested = false;
	private static String BASE_URI;

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		BASE_URI = "http://localhost:" + Settings.getInstance().getApiPort() + "/";
		AU_LOGGER.info(String.format("Starting auto-update service using API via %s", BASE_URI));

		Long buildTimestamp = null; // ms
		int failureCount = 0;

		while (!stopRequested && failureCount < MAX_ATTEMPTS) {
			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				return;
			}

			// If we don't know current node's version then grab that
			if (buildTimestamp == null) {
				// Grab node version and timestamp
				Object response = ApiRequest.perform(BASE_URI + "admin/info", NodeInfo.class, null);
				if (response == null || !(response instanceof NodeInfo)) {
					++failureCount;
					continue;
				}

				NodeInfo nodeInfo = (NodeInfo) response;
				buildTimestamp = nodeInfo.buildTimestamp * 1000L;
				AU_LOGGER.info(String.format("Node's build info: version %s built %s", nodeInfo.buildVersion, nodeInfo.buildTimestamp));

				// API access success
				failureCount = 0;
			}

			// Look for "update" tx which is arbitrary tx with service 1 and timestamp later than buildTimestamp
			// http://localhost:9085/arbitrary/search?txGroupId=1&service=1&confirmationStatus=CONFIRMED&limit=1&reverse=true
			Object response = ApiRequest.perform(BASE_URI + "arbitrary/search", TransactionData.class, ARBITRARY_PARAMS);
			if (response == null || !(response instanceof List<?>)) {
				++failureCount;
				continue;
			}

			List<?> listResponse = (List<?>) response;
			if (listResponse.isEmpty())
				// Not a failure - just no matching transactions yet
				continue;

			if (!(listResponse.get(0) instanceof TransactionData)) {
				++failureCount;
				continue;
			}

			@SuppressWarnings("unchecked")
			TransactionData transactionData = ((List<TransactionData>) listResponse).get(0);
			// API access success
			failureCount = 0;

			if (transactionData.getTimestamp() <= buildTimestamp)
				continue;

			ArbitraryTransactionData arbitraryTxData = (ArbitraryTransactionData) transactionData;
			AU_LOGGER.info(String.format("Found update ARBITRARY transaction %s", Base58.encode(arbitraryTxData.getSignature())));

			// Arbitrary transaction's data contains git commit hash needed to grab JAR:
			// https://github.com/ciyam/MCF/blob/cf86b5f3ce828f75cb18db1b685f2d9e29630d77/MCF-core.jar
			InputStream in = ApiRequest.fetchStream(BASE_URI + "arbitrary/raw/" + Base58.encode(arbitraryTxData.getSignature()));
			if (in == null) {
				AU_LOGGER.warn(String.format("Failed to fetch raw ARBITRARY transaction %s", Base58.encode(arbitraryTxData.getSignature())));
				++failureCount;
				continue;
			}

			byte[] commitHash = new byte[20];
			try {
				in.read(commitHash);
			} catch (IOException e) {
				AU_LOGGER.warn(String.format("Failed to fetch raw ARBITRARY transaction %s", Base58.encode(arbitraryTxData.getSignature())));
				++failureCount;
				continue;
			}

			AU_LOGGER.info(String.format("Update's git commit hash: %s", HashCode.fromBytes(commitHash).toString()));

			String[] autoUpdateRepos = Settings.getInstance().getAutoUpdateRepos();
			for (String repo : autoUpdateRepos)
				if (attemptUpdate(commitHash, repo))
					break;

			// Reset cached node info in case we've updated
			buildTimestamp = null;
			// API access success
			failureCount = 0;
		}

		if (failureCount >= MAX_ATTEMPTS)
			AU_LOGGER.warn("Stopping auto-update service due to API failures");
		else
			AU_LOGGER.info("Stopping auto-update service");
	}

	public void stop() {
		AU_LOGGER.info("Service STOP requested");
		stopRequested = true;
	}

	private static boolean attemptUpdate(byte[] commitHash, String repoBaseUri) {
		Path realJar = Paths.get(System.getProperty("user.dir"), JAR_FILENAME);
		Path oldJar = Paths.get(System.getProperty("user.dir"), "old-" + JAR_FILENAME);

		AU_LOGGER.info(String.format("Fetching update from %s", repoBaseUri));
		InputStream in = ApiRequest.fetchStream(repoBaseUri + "/raw/" + HashCode.fromBytes(commitHash).toString() + "/" + JAR_FILENAME);
		if (in == null) {
			AU_LOGGER.info(String.format("Failed to fetch update from %s", repoBaseUri));
			return false; // failed - try another repo
		}

		Path tmpJar = null;
		try {
			// Save input stream into temporary file
			tmpJar = Files.createTempFile(JAR_FILENAME + "-", null);
			AU_LOGGER.debug(String.format("Saving update from %s into %s", repoBaseUri, tmpJar.toString()));
			Files.copy(in, tmpJar, StandardCopyOption.REPLACE_EXISTING);

			// Keep trying to shutdown node
			int attempt;
			for (attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
				AU_LOGGER.info(String.format("Attempt #%d out of %d to shutdown node", attempt + 1, MAX_ATTEMPTS));
				String response = ApiRequest.perform(BASE_URI + "admin/stop", null);
				if (response == null || !response.equals("true"))
					break;

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// We still need to restart the node!
					break;
				}
			}
			if (attempt == MAX_ATTEMPTS) {
				AU_LOGGER.warn("Failed to shut down node - giving up");
				return true; // repo worked, even if we couldn't shut down node
			}

			// Rename current JAR to 'old' name so we can keep running as Windows locks running JAR
			// The move downloaded JAR into new position
			try {
				Files.deleteIfExists(oldJar);
				Files.move(realJar, oldJar);
				try {
					Files.move(tmpJar, realJar, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					// Put old jar back for now
					AU_LOGGER.warn(String.format("Failed to move downloaded JAR into position: %s", e.getMessage()));
					Files.move(oldJar, realJar, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				// Failed to replace but we still need to restart node
				AU_LOGGER.warn(String.format("Failed to replace JAR: %s", e.getMessage()));
			}

			// Restart node!
			restartNode();

			return true;
		} catch (IOException e) {
			// Couldn't close input stream - fail?
			return false;
		} finally {
			if (tmpJar != null)
				try {
					Files.deleteIfExists(tmpJar);
				} catch (IOException e) {
					// we tried...
					AU_LOGGER.warn(String.format("Failed to delete downloaded JAR: %s", e.getMessage()));
				}

		}
	}

	private static void restartNode() {
		try {
			Path execPath = Paths.get(System.getProperty("user.dir"), NODE_EXE);
			AU_LOGGER.info(String.format("Restarting node via %s", execPath.toString()));
			new ProcessBuilder(execPath.toString()).start();

			// Check node is alive
			int attempt;
			for (attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
				AU_LOGGER.debug(String.format("Attempt #%d out of %d to contact node", attempt + 1, MAX_ATTEMPTS));
				String response = ApiRequest.perform(BASE_URI + "admin/info", null);
				if (response != null)
					break;

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// We still need to check...
					break;
				}
			}

			if (attempt == MAX_ATTEMPTS) {
				AU_LOGGER.warn("Failed to restart node - giving up");
				stopRequested = true;
			}
		} catch (IOException e) {
		}
	}

	// Calls from Controller

	// Auto-update related
	public static void controllerStart() {
		if (!Settings.getInstance().isAutoUpdateEnabled())
			return;

		if (isAutoUpdateRunning()) {
			LOGGER.info("Stopping existing auto-update service");

			// Stop existing auto-update
			stopAutoUpdate();

			// Delete old JAR (if exists)
			Path oldJar = Paths.get(System.getProperty("user.dir"), "old-" + AutoUpdate.JAR_FILENAME);
			try {
				Files.deleteIfExists(oldJar);
			} catch (IOException e) {
				// We tried...
			}
		}

		// Start auto-update
		LOGGER.info("Starting auto-update service");
		startAutoUpdate();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	private static boolean isAutoUpdateRunning() {
		if (isWindows()) {
			try {
				Process process = new ProcessBuilder("cmd.exe", "/c", "sc", "query", SERVICE_NAME).start();
				try (InputStream stdout = process.getInputStream()) {
					InputStreamReader inputStreamReader = new InputStreamReader(stdout);
					BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
					return bufferedReader.lines().anyMatch(line -> line.contains("RUNNING"));
				}
			} catch (IOException e) {
				LOGGER.warn("Failed to query auto-update service", e);
				// Who knows...
				return false;
			}
		} else {
			// TODO: unix poll auto-update
			return false;
		}
	}

	private static void stopAutoUpdate() {
		if (isWindows()) {
			try {
				new ProcessBuilder("cmd.exe", "/c", "sc", "stop", SERVICE_NAME).start();
			} catch (IOException e) {
				LOGGER.warn("Failed to send STOP to auto-update service", e);
				// Carry on regardless?
			}
		} else {
			// TODO: unix stop auto-update
		}
	}

	private static void startAutoUpdate() {
		if (isWindows()) {
			try {
				new ProcessBuilder("cmd.exe", "/c", "sc", "start", SERVICE_NAME).start();
			} catch (IOException e) {
				LOGGER.warn("Failed to start to auto-update service", e);
				// Carry on regardless?
			}
		} else {
			// TODO: unix start auto-update
		}
	}

}
