package org.qora;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;

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

	private static final String JAR_FILENAME = "MCF-core.jar";
	private static final String NODE_EXE = "MCF-core.exe";

	private static final long CHECK_INTERVAL = 1 * 1000;
	private static final int MAX_ATTEMPTS = 10;

	private static final Map<String, String> ARBITRARY_PARAMS = new HashMap<>();
	static {
		ARBITRARY_PARAMS.put("txGroupID", "1"); // dev group
		ARBITRARY_PARAMS.put("service", "1"); // "update" service
		ARBITRARY_PARAMS.put("confirmationStatus", "CONFIRMED");
		ARBITRARY_PARAMS.put("limit", "1");
		ARBITRARY_PARAMS.put("reverse", "true");
	}

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Transactions {
		@XmlAnyElement(lax = true)
		public List<TransactionData> transactions;

		public Transactions() {
		}
	}

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		final String BASE_URI = "http://localhost:" + Settings.getInstance().getApiPort() + "/";

		Long buildTimestamp = null; // ms
		
		while (true) {
			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				return;
			}

			// If we don't know current node's version then grab that
			if (buildTimestamp == null) {
				// Grab node version and timestamp
				Object response = ApiRequest.perform(BASE_URI + "admin/info", NodeInfo.class, null);
				if (response == null || !(response instanceof NodeInfo))
					continue;

				NodeInfo nodeInfo = (NodeInfo) response;
				buildTimestamp = nodeInfo.buildTimestamp * 1000L;
			}

			// Look for "update" tx which is arbitrary tx with service 1 and timestamp later than buildTimestamp
			// http://localhost:9085/arbitrary/search?txGroupId=1&service=1&confirmationStatus=CONFIRMED&limit=1&reverse=true
			Object response = ApiRequest.perform(BASE_URI + "arbitrary/search", TransactionData.class, ARBITRARY_PARAMS);
			if (response == null || !(response instanceof List<?>))
				continue;

			List<?> listResponse = (List<?>) response;
			if (listResponse.isEmpty() || !(listResponse.get(0) instanceof TransactionData))
				continue;

			@SuppressWarnings("unchecked")
			TransactionData transactionData = ((List<TransactionData>) listResponse).get(0);

			if (transactionData.getTimestamp() <= buildTimestamp)
				continue;

			ArbitraryTransactionData arbitraryTxData = (ArbitraryTransactionData) transactionData;

			// Arbitrary transaction's data contains git commit hash needed to grab JAR:
			// https://github.com/ciyam/MCF/blob/cf86b5f3ce828f75cb18db1b685f2d9e29630d77/MCF-core.jar
			InputStream in = ApiRequest.fetchStream(BASE_URI + "arbitrary/raw/" + Base58.encode(arbitraryTxData.getSignature()));
			if (in == null)
				continue;

			byte[] commitHash = new byte[20];
			try {
				in.read(commitHash);
			} catch (IOException e) {
				continue;
			}

			String[] autoUpdateRepos = Settings.getInstance().getAutoUpdateRepos();
			for (String repo : autoUpdateRepos)
				if (attemptUpdate(commitHash, repo, BASE_URI))
					break;

			// Reset cached node info in case we've updated
			buildTimestamp = null;
		}
	}

	private static boolean attemptUpdate(byte[] commitHash, String repoBaseUri, String BASE_URI) {
		Path realJar = Paths.get(System.getProperty("user.dir"), JAR_FILENAME);

		Path tmpJar = null;
		InputStream in = ApiRequest.fetchStream(repoBaseUri + "/raw/" + HashCode.fromBytes(commitHash).toString() + "/" + JAR_FILENAME);
		if (in == null)
			return false;

		try {
			// Save input stream into temporary file
			tmpJar = Files.createTempFile(JAR_FILENAME + "-", null);
			Files.copy(in, tmpJar, StandardCopyOption.REPLACE_EXISTING);

			// Keep trying to shutdown node
			for (int i = 0; i < MAX_ATTEMPTS; ++i) {
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

			try {
				Files.move(tmpJar, realJar, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				// Failed to replace but we still need to restart node
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
				}

		}
	}

	private static void restartNode() {
		try {
			Path execPath = Paths.get(System.getProperty("user.dir"), NODE_EXE);
			new ProcessBuilder(execPath.toString()).start();
		} catch (IOException e) {
		}
	}

}
