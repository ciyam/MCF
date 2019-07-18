package org.qora;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.api.ApiRequest;
import org.qora.controller.AutoUpdate;
import org.qora.settings.Settings;

public class ApplyUpdate {

	static {
		// This static block will be called before others if using ApplyUpdate.main()

		// Log into different files for auto-update - this has to be before LogManger.getLogger() calls
		System.setProperty("log4j2.filenameTemplate", "log-apply-update.txt");

		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static final Logger LOGGER = LogManager.getLogger(ApplyUpdate.class);
	private static final String JAR_FILENAME = AutoUpdate.JAR_FILENAME;
	private static final String NEW_JAR_FILENAME = AutoUpdate.NEW_JAR_FILENAME;

	private static final long CHECK_INTERVAL = 5 * 1000; // ms
	private static final int MAX_ATTEMPTS = 5;

	public static void main(String args[]) {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		LOGGER.info("Applying update...");

		// Shutdown node using API
		if (!shutdownNode())
			return;

		// Replace JAR
		replaceJar();

		// Restart node
		restartNode();
	}

	private static boolean shutdownNode() {
		String BASE_URI = "http://localhost:" + Settings.getInstance().getApiPort() + "/";
		LOGGER.info(String.format("Shutting down node using API via %s", BASE_URI));

		int attempt;
		for (attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
			LOGGER.debug(String.format("Attempt #%d out of %d to shutdown node", attempt + 1, MAX_ATTEMPTS));
			String response = ApiRequest.perform(BASE_URI + "admin/stop", null);
			if (response != null)
				break;

			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				// We still need to check...
				break;
			}
		}

		if (attempt == MAX_ATTEMPTS) {
			LOGGER.error("Failed to shutdown node - giving up");
			return false;
		}

		return true;
	}

	private static void replaceJar() {
		// Assuming current working directory contains the JAR files
		Path realJar = Paths.get(JAR_FILENAME);
		Path newJar = Paths.get(NEW_JAR_FILENAME);

		try {
			Files.copy(newJar, realJar, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn(String.format("Failed to copy %s to %s", newJar.toString(), realJar.toString()), e.getMessage());
			// Fall-through to restarting node...
		}
	}

	private static void restartNode() {
		String javaHome = System.getProperty("java.home");
		LOGGER.debug(String.format("Java home: %s", javaHome));

		Path javaBinary = Paths.get(javaHome, "bin", "java");
		LOGGER.debug(String.format("Java binary: %s", javaBinary));

		try {
			List<String> javaCmd = Arrays.asList(javaBinary.toString(), "-jar", JAR_FILENAME);
			LOGGER.info(String.format("Restarting node with: %s", String.join(" ", javaCmd)));
			
			new ProcessBuilder(javaCmd).start();
		} catch (IOException e) {
			LOGGER.error(String.format("Failed to restart node (BAD): %s", e.getMessage()));
		}
	}

}
