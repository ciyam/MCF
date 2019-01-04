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
	private boolean wipeUnconfirmedOnStart = true;
	private String blockchainConfigPath = "blockchain.json";

	// RPC
	private int rpcPort = 9085;
	private List<String> rpcAllowed = new ArrayList<String>(Arrays.asList("127.0.0.1", "::1")); // ipv4, ipv6
	private boolean rpcEnabled = true;

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
		// RPC
		if (json.containsKey("rpcport"))
			this.rpcPort = ((Long) json.get("rpcport")).intValue();

		if (json.containsKey("rpcallowed")) {
			JSONArray allowedArray = (JSONArray) json.get("rpcallowed");

			this.rpcAllowed = new ArrayList<String>();

			for (Object entry : allowedArray) {
				if (!(entry instanceof String))
					throw new RuntimeException("Entry inside 'rpcallowed' is not string");

				this.rpcAllowed.add((String) entry);
			}
		}

		if (json.containsKey("rpcenabled"))
			this.rpcEnabled = ((Boolean) json.get("rpcenabled")).booleanValue();

		// Blockchain config

		if (json.containsKey("wipeUnconfirmedOnStart"))
			this.wipeUnconfirmedOnStart = (Boolean) getTypedJson(json, "wipeUnconfirmedOnStart", Boolean.class);

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

	public int getRpcPort() {
		return this.rpcPort;
	}

	public List<String> getRpcAllowed() {
		return this.rpcAllowed;
	}

	public boolean isRpcEnabled() {
		return this.rpcEnabled;
	}

	public boolean useBitcoinTestNet() {
		return this.useBitcoinTestNet;
	}

	public boolean getWipeUnconfirmedOnStart() {
		return this.wipeUnconfirmedOnStart;
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
