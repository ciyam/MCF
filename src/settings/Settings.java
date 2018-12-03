package settings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import qora.block.GenesisBlock;

public class Settings {

	// Properties
	private static Settings instance;
	private long genesisTimestamp = GenesisBlock.GENESIS_TIMESTAMP;
	private int maxBytePerFee = 1024;
	private String userpath = "";

	// RPC
	private int rpcPort = 9085;
	private List<String> rpcAllowed = new ArrayList<String>(Arrays.asList("127.0.0.1", "::1")); // ipv4, ipv6
	private boolean rpcEnabled = true;
	
	// Globalization
	private String translationsPath = "globalization/";
	private String[] translationsDefaultLocales = {"en"};
	
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

				process(settingsJSON);

				this.userpath = path;
				break;
			} while (true);
		} catch (IOException | ClassCastException e) {

		}
	}

	// Other methods

	public static Settings getInstance() {
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
		if (json.containsKey("testnetstamp")) {
			if (json.get("testnetstamp").toString().equals("now") || ((Long) json.get("testnetstamp")).longValue() == 0) {
				this.genesisTimestamp = System.currentTimeMillis();
			} else {
				this.genesisTimestamp = ((Long) json.get("testnetstamp")).longValue();
			}
		}
		
		// RPC
		if(json.containsKey("rpcport"))
		{
			this.rpcPort = ((Long) json.get("rpcport")).intValue();
		}

		if(json.containsKey("rpcallowed"))
		{
			JSONArray allowedArray = (JSONArray) json.get("rpcallowed");
			this.rpcAllowed = new ArrayList<String>(allowedArray);	
		}
		
		if(json.containsKey("rpcenabled"))
		{
			this.rpcEnabled = ((Boolean) json.get("rpcenabled")).booleanValue();
		}
		
		// Globalization
		if(json.containsKey("translationspath"))
		{
			this.translationsPath = ((String) json.get("translationspath"));
		}

		if(json.containsKey("translationsdefaultlocales"))
		{
			this.translationsDefaultLocales = ((String[]) json.get("translationsdefaultlocales"));
		}
	}

	public boolean isTestNet() {
		return this.genesisTimestamp != GenesisBlock.GENESIS_TIMESTAMP;
	}

	// Getters / setters

	public int getMaxBytePerFee() {
		return this.maxBytePerFee;
	}

	public long getGenesisTimestamp() {
		return this.genesisTimestamp;
	}

	public String getUserpath() {
		return this.userpath;
	}

	public int getRpcPort()
	{
		return this.rpcPort;
	}
	
	public List<String> getRpcAllowed()
	{
		return this.rpcAllowed;
	}

	public boolean isRpcEnabled() 
	{
		return this.rpcEnabled;
	}
	
	public String translationsPath()
	{
		return this.translationsPath;
	}
	
	public String[] translationsDefaultLocales()
	{
		return this.translationsDefaultLocales;
	}
}
