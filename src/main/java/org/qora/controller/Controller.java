package org.qora.controller;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.api.ApiService;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.data.block.BlockData;
import org.qora.data.network.PeerData;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.Message;
import org.qora.network.message.SignaturesMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;
import org.qora.utils.Base58;
import org.qora.utils.NTP;

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

	/** Lock for only allowing one blockchain-modifying codepath at a time. e.g. synchronization or newly generated block. */
	private final Lock blockchainLock;

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

		blockchainLock = new ReentrantLock();
	}

	public static Controller getInstance() {
		if (instance == null)
			instance = new Controller();

		return instance;
	}

	// Getters / setters

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

	/** Returns current blockchain height, or 0 if there's a repository issue */
	public int getChainHeight() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			LOGGER.error("Repository issue when fetching blockchain height", e);
			return 0;
		}
	}

	public Lock getBlockchainLock() {
		return this.blockchainLock;
	}

	// Entry point

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

		// XXX extract private key needed for block gen
		if (args.length == 0 || !args[0].equals("NO-BLOCK-GEN")) {
			LOGGER.info("Starting block generator");
			byte[] privateKey = Base58.decode(args.length > 0 ? args[0] : "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
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
				Thread.currentThread().setName("Shutdown hook");

				Controller.getInstance().shutdown();
			}
		});

		LOGGER.info("Starting controller");
		Controller.getInstance().start();
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		try {
			while (!isStopping) {
				Thread.sleep(1000);

				potentiallySynchronize();

				// Query random connections for unconfirmed transactions
			}
		} catch (InterruptedException e) {
			// time to exit
			return;
		}
	}

	private void potentiallySynchronize() {
		int ourHeight = getChainHeight();
		if (ourHeight == 0)
			return;

		// If we have enough peers, potentially synchronize
		List<Peer> peers = Network.getInstance().getHandshakeCompletedPeers();
		if (peers.size() >= Settings.getInstance().getMinPeers()) {
			peers.removeIf(peer -> peer.getPeerData().getLastHeight() <= ourHeight);

			if (!peers.isEmpty()) {
				// Pick random peer to sync with
				int index = new SecureRandom().nextInt(peers.size());
				Peer peer = peers.get(index);

				if (!Synchronizer.getInstance().synchronize(peer)) {
					// Failure so don't use this peer again for a while
					try (final Repository repository = RepositoryManager.getRepository()) {
						PeerData peerData = peer.getPeerData();
						peerData.setLastMisbehaved(NTP.getTime());
						repository.getNetworkRepository().save(peerData);
						repository.saveChanges();
					} catch (DataException e) {
						LOGGER.warn("Repository issue while updating peer synchronization info", e);
					}
				}
			}
		}
	}

	// Shutdown

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down controller");
				this.interrupt();
				try {
					this.join();
				} catch (InterruptedException e) {
					// We were interrupted while waiting for thread to join
				}

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
						// We were interrupted while waiting for thread to join
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

	// Callbacks for/from network

	public void doNetworkBroadcast() {
		Network network = Network.getInstance();

		// Send our known peers
		network.broadcast(peer -> network.buildPeersMessage(peer));

		// Send our current height
		network.broadcast(peer -> new HeightMessage(this.getChainHeight()));
	}

	public void onGeneratedBlock(BlockData newBlockData) {
		// XXX we should really be broadcasting the new block sig, not height
		// Could even broadcast top two block sigs so that remote peers can see new block references current network-wide last block

		// Broadcast our new height
		Network.getInstance().broadcast(peer -> new HeightMessage(newBlockData.getHeight()));
	}

	public void onNetworkMessage(Peer peer, Message message) {
		LOGGER.trace(String.format("Processing %s message from %s", message.getType().name(), peer));

		switch (message.getType()) {
			case HEIGHT:
				HeightMessage heightMessage = (HeightMessage) message;

				// Update our record of peer's height
				peer.getPeerData().setLastHeight(heightMessage.getHeight());

				break;

			case GET_SIGNATURES:
				try (final Repository repository = RepositoryManager.getRepository()) {
					GetSignaturesMessage getSignaturesMessage = (GetSignaturesMessage) message;
					byte[] parentSignature = getSignaturesMessage.getParentSignature();

					List<byte[]> signatures = new ArrayList<>();

					do {
						BlockData blockData = repository.getBlockRepository().fromReference(parentSignature);

						if (blockData == null)
							break;

						parentSignature = blockData.getSignature();
						signatures.add(parentSignature);
					} while (signatures.size() < 500);

					Message signaturesMessage = new SignaturesMessage(signatures);
					signaturesMessage.setId(message.getId());
					if (!peer.sendMessage(signaturesMessage))
						peer.disconnect();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while responding to %s from peer %s", message.getType().name(), peer), e);
				}
				break;

			case GET_BLOCK:
				try (final Repository repository = RepositoryManager.getRepository()) {
					GetBlockMessage getBlockMessage = (GetBlockMessage) message;
					byte[] signature = getBlockMessage.getSignature();

					BlockData blockData = repository.getBlockRepository().fromSignature(signature);
					if (blockData == null)
						// No response at all???
						break;

					Block block = new Block(repository, blockData);

					Message blockMessage = new BlockMessage(block);
					blockMessage.setId(message.getId());
					if (!peer.sendMessage(blockMessage))
						peer.disconnect();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while responding to %s from peer %s", message.getType().name(), peer), e);
				}
				break;

			default:
				break;
		}
	}

}
