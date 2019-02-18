package org.qora.settings;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.qora.block.BlockChain;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Settings {

	private static final Logger LOGGER = LogManager.getLogger(Settings.class);

	// Properties
	private static Settings instance;
	private String userpath = "";
	private boolean useBitcoinTestNet = false;
	private boolean wipeUnconfirmedOnStart = false;
	private String blockchainConfigPath = "blockchain.json";
	/** Maximum number of unconfirmed transactions allowed per account */
	private int maxUnconfirmedPerAccount = 100;
	/** Max milliseconds into future for accepting new, unconfirmed transactions */
	private long maxTransactionTimestampFuture = 24 * 60 * 60 * 1000; // milliseconds

	// API
	private int apiPort = 9085;
	private List<String> apiAllowed = new ArrayList<String>(Arrays.asList("127.0.0.1", "::1")); // ipv4, ipv6
	private boolean apiEnabled = true;

	// Peer-to-peer networking
	public static final int DEFAULT_LISTEN_PORT = 9084;
	private int listenPort = DEFAULT_LISTEN_PORT;
	private String bindAddress = null; // listen on all local addresses
	private int minPeers = 3;
	private int maxPeers = 10;

	// Constants
	private static final String SETTINGS_FILENAME = "settings.json";

	// Constructors

	private Settings() {
	}

	private Settings(String filename) {
		// Read from file
		String path = "";

		try {
			do {
				File file = new File(path + filename);

				if (!file.exists()) {
					// log lack of settings file
					LOGGER.info("Settings file not found: " + path + filename);
					break;
				}

				LOGGER.info("Using settings file: " + path + filename);
				List<String> lines = Files.readLines(file, Charsets.UTF_8);

				// Concatenate lines for JSON parsing
				String jsonString = "";
				for (String line : lines) {
					// Escape single backslashes in "userpath" entries, typically Windows-style paths
					if (line.contains("userpath"))
						line.replace("\\", "\\\\");

					jsonString += line;
				}

				JSONObject settingsJSON = (JSONObject) JSONValue.parse(jsonString);

				String userpath = (String) settingsJSON.get("userpath");
				if (userpath != null) {
					path = userpath;

					// Add trailing directory separator if needed
					if (!path.endsWith(File.separator))
						path += File.separator;

					continue;
				}

				this.userpath = path;
				process(settingsJSON);

				break;
			} while (true);
		} catch (IOException | ClassCastException e) {
			LOGGER.error("Unable to parse settings file: " + path + filename);
			throw new RuntimeException("Unable to parse settings file", e);
		}
	}

	// Other methods

	public static synchronized Settings getInstance() {
		if (instance == null)
			instance = new Settings(SETTINGS_FILENAME);

		return instance;
	}

	public static void test(JSONObject settingsJSON) {
		// Discard previous settings
		if (instance != null)
			instance = null;

		instance = new Settings();
		getInstance().process(settingsJSON);
	}

	private void process(JSONObject json) {
		// API
		if (json.containsKey("apiPort"))
			this.apiPort = ((Long) json.get("apiPort")).intValue();

		if (json.containsKey("apiAllowed")) {
			JSONArray allowedArray = (JSONArray) json.get("apiAllowed");

			this.apiAllowed = new ArrayList<String>();

			for (Object entry : allowedArray) {
				if (!(entry instanceof String))
					throw new RuntimeException("Entry inside 'apiAllowed' is not string");

				this.apiAllowed.add((String) entry);
			}
		}

		if (json.containsKey("apiEnabled"))
			this.apiEnabled = ((Boolean) json.get("apiEnabled")).booleanValue();

		// Peer-to-peer networking

		if (json.containsKey("listenPort"))
			this.listenPort = ((Long) getTypedJson(json, "listenPort", Long.class)).intValue();

		if (json.containsKey("bindAddress"))
			this.bindAddress = (String) getTypedJson(json, "bindAddress", String.class);

		if (json.containsKey("minPeers"))
			this.minPeers = ((Long) getTypedJson(json, "minPeers", Long.class)).intValue();

		if (json.containsKey("maxPeers"))
			this.maxPeers = ((Long) getTypedJson(json, "maxPeers", Long.class)).intValue();

		// Node-specific behaviour

		if (json.containsKey("wipeUnconfirmedOnStart"))
			this.wipeUnconfirmedOnStart = (Boolean) getTypedJson(json, "wipeUnconfirmedOnStart", Boolean.class);

		if (json.containsKey("maxUnconfirmedPerAccount"))
			this.maxUnconfirmedPerAccount = ((Long) getTypedJson(json, "maxUnconfirmedPerAccount", Long.class)).intValue();

		if (json.containsKey("maxTransactionTimestampFuture"))
			this.maxTransactionTimestampFuture = (Long) getTypedJson(json, "maxTransactionTimestampFuture", Long.class);

		// Blockchain config

		if (json.containsKey("blockchainConfig"))
			blockchainConfigPath = (String) getTypedJson(json, "blockchainConfig", String.class);

		File file = new File(this.userpath + blockchainConfigPath);

		if (!file.exists()) {
			LOGGER.info("Blockchain config file not found: " + this.userpath + blockchainConfigPath);
			throw new RuntimeException("Unable to read blockchain config file");
		}

		try {
			List<String> lines = Files.readLines(file, Charsets.UTF_8);
			JSONObject blockchainJSON = (JSONObject) JSONValue.parse(String.join("\n", lines));
			BlockChain.fromJSON(blockchainJSON);
		} catch (IOException e) {
			LOGGER.error("Unable to parse blockchain config file: " + this.userpath + blockchainConfigPath);
			throw new RuntimeException("Unable to parse blockchain config file", e);
		}
	}

	// Getters / setters

	public String getUserpath() {
		return this.userpath;
	}

	public int getApiPort() {
		return this.apiPort;
	}

	public List<String> getApiAllowed() {
		return this.apiAllowed;
	}

	public boolean isApiEnabled() {
		return this.apiEnabled;
	}

	public int getListenPort() {
		return this.listenPort;
	}

	public int getDefaultListenPort() {
		return DEFAULT_LISTEN_PORT;
	}

	public String getBindAddress() {
		return this.bindAddress;
	}

	public int getMinPeers() {
		return this.minPeers;
	}

	public int getMaxPeers() {
		return this.maxPeers;
	}

	public boolean useBitcoinTestNet() {
		return this.useBitcoinTestNet;
	}

	public boolean getWipeUnconfirmedOnStart() {
		return this.wipeUnconfirmedOnStart;
	}

	public int getMaxUnconfirmedPerAccount() {
		return this.maxUnconfirmedPerAccount;
	}

	public long getMaxTransactionTimestampFuture() {
		return this.maxTransactionTimestampFuture;
	}

	// Config parsing

	public static Object getTypedJson(JSONObject json, String key, Class<?> clazz) {
		if (!json.containsKey(key)) {
			LOGGER.error("Missing \"" + key + "\" in blockchain config file");
			throw new RuntimeException("Missing \"" + key + "\" in blockchain config file");
		}

		Object value = json.get(key);
		if (!clazz.isInstance(value)) {
			LOGGER.error("\"" + key + "\" not " + clazz.getSimpleName() + " in blockchain config file");
			throw new RuntimeException("\"" + key + "\" not " + clazz.getSimpleName() + " in blockchain config file");
		}

		return value;
	}

	public static BigDecimal getJsonBigDecimal(JSONObject json, String key) {
		try {
			return new BigDecimal((String) getTypedJson(json, key, String.class));
		} catch (NumberFormatException e) {
			LOGGER.error("Unable to parse \"" + key + "\" in blockchain config file");
			throw new RuntimeException("Unable to parse \"" + key + "\" in blockchain config file");
		}
	}

	public static Long getJsonQuotedLong(JSONObject json, String key) {
		try {
			return Long.parseLong((String) getTypedJson(json, key, String.class));
		} catch (NumberFormatException e) {
			LOGGER.error("Unable to parse \"" + key + "\" in blockchain config file");
			throw new RuntimeException("Unable to parse \"" + key + "\" in blockchain config file");
		}
	}

}
