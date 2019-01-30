package org.qora.controller;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.api.ApiService;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.data.block.BlockData;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.Message;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;
import org.qora.utils.Base58;

public class Controller extends Thread {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	public static final String connectionUrl = "jdbc:hsqldb:file:db/blockchain;create=true";
	public static final long startTime = System.currentTimeMillis();
	public static final String VERSION_PREFIX = "qora-core-";

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);
	private static final Object shutdownLock = new Object();
	private static boolean isStopping = false;
	private static BlockGenerator blockGenerator = null;
	private static Controller instance;
	private final String buildVersion;
	private final long buildTimestamp;

	private Controller() {
		Properties properties = new Properties();
		try (InputStream in = ClassLoader.getSystemResourceAsStream("build.properties")) {
			properties.load(in);
		} catch (IOException e) {
			throw new RuntimeException("Can't read build.properties resource", e);
		}

		String buildTimestamp = properties.getProperty("build.timestamp");
		if (buildTimestamp == null)
			throw new RuntimeException("Can't read build.timestamp from build.properties resource");

		this.buildTimestamp = LocalDateTime.parse(buildTimestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")).toEpochSecond(ZoneOffset.UTC);

		String buildVersion = properties.getProperty("build.version");
		if (buildVersion == null)
			throw new RuntimeException("Can't read build.version from build.properties resource");

		this.buildVersion = VERSION_PREFIX + buildVersion;
	}

	public static Controller getInstance() {
		if (instance == null)
			instance = new Controller();

		return instance;
	}

	public static void main(String args[]) {
		LOGGER.info("Starting up...");

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		Settings.getInstance();

		LOGGER.info("Starting repository");
		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			LOGGER.error("Unable to start repository", e);
			System.exit(1);
		}

		LOGGER.info("Validating blockchain");
		try {
			BlockChain.validate();
		} catch (DataException e) {
			LOGGER.error("Couldn't validate blockchain", e);
			System.exit(2);
		}

		// XXX work to be done here!
		if (args.length == 0) {
			LOGGER.info("Starting block generator");
			byte[] privateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
			blockGenerator = new BlockGenerator(privateKey);
			blockGenerator.start();
		}

		LOGGER.info("Starting API on port " + Settings.getInstance().getApiPort());
		try {
			ApiService apiService = ApiService.getInstance();
			apiService.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start API", e);
			System.exit(1);
		}

		LOGGER.info("Starting networking");
		try {
			Network network = Network.getInstance();
			network.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start networking", e);
			System.exit(1);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Controller.getInstance().shutdown();
			}
		});

		LOGGER.info("Starting controller");
		Controller.getInstance().start();
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		try {
			while (true) {
				Thread.sleep(1000);

				// Query random connections for their blockchain status
				// If height > ours then potentially synchronize

				// Query random connections for unconfirmed transactions
			}
		} catch (InterruptedException e) {
			// time to exit
			return;
		}
	}

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down controller");
				this.interrupt();

				LOGGER.info("Shutting down networking");
				Network.getInstance().shutdown();

				LOGGER.info("Shutting down API");
				ApiService.getInstance().stop();

				if (blockGenerator != null) {
					LOGGER.info("Shutting down block generator");
					blockGenerator.shutdown();
					try {
						blockGenerator.join();
					} catch (InterruptedException e) {
						// We were interrupted while waiting for thread to 'join'
					}
				}

				try {
					LOGGER.info("Shutting down repository");
					RepositoryManager.closeRepositoryFactory();
				} catch (DataException e) {
					LOGGER.error("Error occurred while shutting down repository", e);
				}

				LOGGER.info("Shutdown complete!");
			}
		}
	}

	public void shutdownAndExit() {
		this.shutdown();
		System.exit(0);
	}

	public byte[] getMessageMagic() {
		return new byte[] {
			0x12, 0x34, 0x56, 0x78
		};
	}

	public long getBuildTimestamp() {
		return this.buildTimestamp;
	}

	public String getVersionString() {
		return this.buildVersion;
	}

	public int getChainHeight() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			LOGGER.error("Repository issue when fetching blockchain height", e);
			return 0;
		}
	}

	// Callbacks for/from network

	public void doNetworkBroadcast() {
		Network network = Network.getInstance();

		// Send our known peers
		network.broadcast(network.buildPeersMessage());

		// Send our current height
		network.broadcast(new HeightMessage(this.getChainHeight()));
	}

	public void onGeneratedBlock(BlockData newBlockData) {
		// XXX we should really be broadcasting the new block sig, not height
		// Could even broadcast top two block sigs so that remote peers can see new block references current network-wide last block

		// Broadcast our new height
		Network.getInstance().broadcast(new HeightMessage(newBlockData.getHeight()));
	}

	public void onNetworkMessage(Peer peer, Message message) {
		LOGGER.trace(String.format("Processing %s message from %s", message.getType().name(), peer.getRemoteSocketAddress()));

		switch (message.getType()) {
			case HEIGHT:
				HeightMessage heightMessage = (HeightMessage) message;

				// If we connected to peer, then update our record of peer's height
				if (peer.isOutbound())
					peer.getPeerData().setLastHeight(heightMessage.getHeight());

				// XXX we should instead test incoming block sigs to see if we have them, and if not do sync
				// Is peer's blockchain longer than ours?
				if (heightMessage.getHeight() > getChainHeight())
					Synchronizer.getInstance().synchronize(peer);
				break;

			default:
				break;
		}
	}

}
