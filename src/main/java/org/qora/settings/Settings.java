package org.qora.settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qora.block.BlockChain;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class Settings {

	private static final int MAINNET_LISTEN_PORT = 9889;
	private static final int TESTNET_LISTEN_PORT = 9989;

	private static final int MAINNET_API_PORT = 9888;
	private static final int TESTNET_API_PORT = 9988;

	private static final int MAINNET_UI_PORT = 9880;
	private static final int TESTNET_UI_PORT = 9980;

	private static final Logger LOGGER = LogManager.getLogger(Settings.class);
	private static final String SETTINGS_FILENAME = "settings.json";

	// Properties
	private static Settings instance;

	// Settings, and other config files
	private String userPath;

	// Node management UI
	private boolean uiEnabled = true;
	private Integer uiPort;
	private String[] uiWhitelist = new String[] {
		"::1", "127.0.0.1"
	};

	// API-related
	private boolean apiEnabled = true;
	private Integer apiPort;
	private String[] apiWhitelist = new String[] {
		"::1", "127.0.0.1"
	};
	private Boolean apiRestricted;
	private boolean apiLoggingEnabled = false;

	// Specific to this node
	private boolean wipeUnconfirmedOnStart = false;
	/** Maximum number of unconfirmed transactions allowed per account */
	private int maxUnconfirmedPerAccount = 100;
	/** Max milliseconds into future for accepting new, unconfirmed transactions */
	private int maxTransactionTimestampFuture = 24 * 60 * 60 * 1000; // milliseconds
	// auto-update
	private boolean autoUpdateEnabled = true;

	// Peer-to-peer related
	private boolean isTestNet = false;
	private Integer listenPort;
	private String bindAddress = "::"; // Use IPv6 wildcard to listen on all local addresses
	/** Minimum number of peers to allow block generation / synchronization. */
	private int minBlockchainPeers = 3;
	/** Target number of outbound connections to peers we should make. */
	private int minOutboundPeers = 10;
	/** Maximum number of peer connections we allow. */
	private int maxPeers = 30;

	// Which blockchains this node is running
	private String blockchainConfig = null; // use default from resources
	private boolean useBitcoinTestNet = false;

	// Repository related
	/** Queries that take longer than this are logged. (milliseconds) */
	private Long slowQueryThreshold = null;
	/** Repository storage path. */
	private String repositoryPath = "db";

	// Auto-update sources
	private String[] autoUpdateRepos = new String[] {
		"https://github.com/ciyam/MCF/raw/%s/MCF-core.update",
		"https://raw.githubusercontent.com/ciyam/MCF/%s/MCF-core.update",
		"https://raw.githubusercontent.com@151.101.16.133/ciyam/MCF/%s/MCF-core.update",
		"https://www.mcfamily.io/updates/%s",
		"https://www.mcfamily.io@47.246.1.213/updates/%s"
	};

	// Constructors

	private Settings() {
	}

	// Other methods

	public static synchronized Settings getInstance() {
		if (instance == null)
			fileInstance(SETTINGS_FILENAME);

		return instance;
	}

	public static void fileInstance(String filename) {
		JAXBContext jc;
		Unmarshaller unmarshaller;

		try {
			// Create JAXB context aware of Settings
			jc = JAXBContextFactory.createContext(new Class[] {
				Settings.class
			}, null);

			// Create unmarshaller
			unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
		} catch (JAXBException e) {
			LOGGER.error("Unable to process settings file", e);
			throw new RuntimeException("Unable to process settings file", e);
		}

		Settings settings = null;
		String path = "";

		do {
			LOGGER.info("Using settings file: " + path + filename);

			// Create the StreamSource by creating Reader to the JSON input
			try (Reader settingsReader = new FileReader(path + filename)) {
				StreamSource json = new StreamSource(settingsReader);

				// Attempt to unmarshal JSON stream to Settings
				settings = unmarshaller.unmarshal(json, Settings.class).getValue();
			} catch (FileNotFoundException e) {
				LOGGER.error("Settings file not found: " + path + filename);
				throw new RuntimeException("Settings file not found: " + path + filename);
			} catch (UnmarshalException e) {
				Throwable linkedException = e.getLinkedException();
				if (linkedException instanceof XMLMarshalException) {
					String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();
					LOGGER.error(message);
					throw new RuntimeException(message);
				}

				LOGGER.error("Unable to process settings file", e);
				throw new RuntimeException("Unable to process settings file", e);
			} catch (JAXBException e) {
				LOGGER.error("Unable to process settings file", e);
				throw new RuntimeException("Unable to process settings file", e);
			} catch (IOException e) {
				LOGGER.error("Unable to process settings file", e);
				throw new RuntimeException("Unable to process settings file", e);
			}

			if (settings.userPath != null) {
				// Adjust filename and go round again
				path = settings.userPath;

				// Add trailing directory separator if needed
				if (!path.endsWith(File.separator))
					path += File.separator;
			}
		} while (settings.userPath != null);

		// Validate settings
		settings.validate();

		// Minor fix-up
		settings.userPath = path;

		// Successfully read settings now in effect
		instance = settings;

		// Now read blockchain config
		BlockChain.fileInstance(settings.getUserPath(), settings.getBlockchainConfig());
	}

	private void validate() {
		// Validation goes here
	}

	// Getters / setters

	public String getUserPath() {
		return this.userPath;
	}

	public boolean isUiEnabled() {
		return this.uiEnabled;
	}

	public int getUiPort() {
		if (this.uiPort != null)
			return this.uiPort;

		return this.isTestNet ? TESTNET_UI_PORT : MAINNET_UI_PORT;
	}

	public String[] getUiWhitelist() {
		return this.uiWhitelist;
	}

	public boolean isApiEnabled() {
		return this.apiEnabled;
	}

	public int getApiPort() {
		if (this.apiPort != null)
			return this.apiPort;

		return this.isTestNet ? TESTNET_API_PORT : MAINNET_API_PORT;
	}

	public String[] getApiWhitelist() {
		return this.apiWhitelist;
	}

	public boolean isApiRestricted() {
		// Explicitly set value takes precedence
		if (this.apiRestricted != null)
			return this.apiRestricted;

		// Not set in config file, so restrict if not testnet
		return !BlockChain.getInstance().isTestChain();
	}

	public boolean isApiLoggingEnabled() {
		return this.apiLoggingEnabled;
	}

	public boolean getWipeUnconfirmedOnStart() {
		return this.wipeUnconfirmedOnStart;
	}

	public int getMaxUnconfirmedPerAccount() {
		return this.maxUnconfirmedPerAccount;
	}

	public int getMaxTransactionTimestampFuture() {
		return this.maxTransactionTimestampFuture;
	}

	public boolean isTestNet() {
		return this.isTestNet;
	}

	public int getListenPort() {
		if (this.listenPort != null)
			return this.listenPort;

		return this.isTestNet ? TESTNET_LISTEN_PORT : MAINNET_LISTEN_PORT;
	}

	public int getDefaultListenPort() {
		return this.isTestNet ? TESTNET_LISTEN_PORT : MAINNET_LISTEN_PORT;
	}

	public String getBindAddress() {
		return this.bindAddress;
	}

	public int getMinBlockchainPeers() {
		return this.minBlockchainPeers;
	}

	public int getMinOutboundPeers() {
		return this.minOutboundPeers;
	}

	public int getMaxPeers() {
		return this.maxPeers;
	}

	public String getBlockchainConfig() {
		return this.blockchainConfig;
	}

	public boolean useBitcoinTestNet() {
		return this.useBitcoinTestNet;
	}

	public Long getSlowQueryThreshold() {
		return this.slowQueryThreshold;
	}

	public String getRepositoryPath() {
		return this.repositoryPath;
	}

	public boolean isAutoUpdateEnabled() {
		return this.autoUpdateEnabled;
	}

	public String[] getAutoUpdateRepos() {
		return this.autoUpdateRepos;
	}

}
