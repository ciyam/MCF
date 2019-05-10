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
import org.qora.data.network.BlockSummaryData;
import org.qora.data.network.PeerData;
import org.qora.data.transaction.TransactionData;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.BlockSummariesMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetBlockSummariesMessage;
import org.qora.network.message.GetPeersMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.Message;
import org.qora.network.message.SignaturesMessage;
import org.qora.network.message.TransactionMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;
import org.qora.utils.Base58;
import org.qora.utils.NTP;

public class Controller extends Thread {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	public static final long startTime = System.currentTimeMillis();
	public static final String VERSION_PREFIX = "qora-core-";

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);
	private static final long MISBEHAVIOUR_COOLOFF = 24 * 60 * 60 * 1000; // ms
	private static final Object shutdownLock = new Object();
	private static final String repositoryUrlTemplate = "jdbc:hsqldb:file:%s/blockchain;create=true";

	private static boolean isStopping = false;
	private static BlockGenerator blockGenerator = null;
	private static Controller instance;
	private final String buildVersion;
	private final long buildTimestamp; // seconds

	/** Lock for only allowing one blockchain-modifying codepath at a time. e.g. synchronization or newly generated block. */
	private final ReentrantLock blockchainLock;

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
		LOGGER.info(String.format("Build timestamp: %s", buildTimestamp));

		String buildVersion = properties.getProperty("build.version");
		if (buildVersion == null)
			throw new RuntimeException("Can't read build.version from build.properties resource");

		this.buildVersion = VERSION_PREFIX + buildVersion;
		LOGGER.info(String.format("Build version: %s", this.buildVersion));

		blockchainLock = new ReentrantLock();
	}

	public static Controller getInstance() {
		if (instance == null)
			instance = new Controller();

		return instance;
	}

	// Getters / setters

	public static String getRepositoryUrl() {
		return String.format(repositoryUrlTemplate, Settings.getInstance().getRepositoryPath());
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

	/** Returns current blockchain height, or 0 if there's a repository issue */
	public int getChainHeight() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			LOGGER.error("Repository issue when fetching blockchain height", e);
			return 0;
		}
	}

	public ReentrantLock getBlockchainLock() {
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
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(getRepositoryUrl());
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

		LOGGER.info("Starting block generator");
		blockGenerator = new BlockGenerator();
		blockGenerator.start();

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

		// Auto-update service
		LOGGER.info("Starting auto-update");
		AutoUpdate.getInstance().start();
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		try {
			while (!isStopping) {
				Thread.sleep(10000);

				potentiallySynchronize();

				// Query random connections for unconfirmed transactions?
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
		if (peers.size() < Settings.getInstance().getMinPeers())
			return;

		for(Peer peer : peers)
			LOGGER.trace(String.format("Peer %s is at height %d", peer, peer.getPeerData().getLastHeight()));

		// Remove peers with unknown, or same, height
		peers.removeIf(peer -> {
			Integer peerHeight = peer.getPeerData().getLastHeight();
			return peerHeight == null;
		});

		// Remove peers that have "misbehaved" recently
		peers.removeIf(peer -> {
			Long lastMisbehaved = peer.getPeerData().getLastMisbehaved();
			return lastMisbehaved != null && lastMisbehaved > NTP.getTime() - MISBEHAVIOUR_COOLOFF;
		});

		if (!peers.isEmpty()) {
			// Pick random peer to sync with
			int index = new SecureRandom().nextInt(peers.size());
			Peer peer = peers.get(index);

			if (!Synchronizer.getInstance().synchronize(peer)) {
				LOGGER.debug(String.format("Failed to synchronize with peer %s", peer));

				// Failure so don't use this peer again for a while
				try (final Repository repository = RepositoryManager.getRepository()) {
					PeerData peerData = peer.getPeerData();
					peerData.setLastMisbehaved(NTP.getTime());
					repository.getNetworkRepository().save(peerData);
					repository.saveChanges();
				} catch (DataException e) {
					LOGGER.warn("Repository issue while updating peer synchronization info", e);
				}

				return;
			}

			LOGGER.debug(String.format("Synchronized with peer %s", peer));

			// Broadcast our new height (if changed)
			int updatedHeight = getChainHeight();
			if (updatedHeight != ourHeight)
				Network.getInstance().broadcast(recipientPeer -> new HeightMessage(updatedHeight));
		}
	}

	// Shutdown

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down auto-update");
				AutoUpdate.getInstance().shutdown();

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

		// Request peers lists
		network.broadcast(peer -> new GetPeersMessage());
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
					} while (signatures.size() < Network.MAX_SIGNATURES_PER_REPLY);

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
					if (blockData == null) {
						LOGGER.trace(String.format("Ignoring GET_BLOCK request from peer %s for unknown block %s", peer, Base58.encode(signature)));
						// Send no response at all???
						break;
					}

					Block block = new Block(repository, blockData);

					Message blockMessage = new BlockMessage(block);
					blockMessage.setId(message.getId());
					if (!peer.sendMessage(blockMessage))
						peer.disconnect();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while responding to %s from peer %s", message.getType().name(), peer), e);
				}
				break;

			case TRANSACTION:
				try (final Repository repository = RepositoryManager.getRepository()) {
					TransactionMessage transactionMessage = (TransactionMessage) message;

					TransactionData transactionData = transactionMessage.getTransactionData();
					Transaction transaction = Transaction.fromData(repository, transactionData);

					// Check signature
					if (!transaction.isSignatureValid()) {
						LOGGER.trace(String.format("Ignoring TRANSACTION %s with invalid signature from peer %s", Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					// Do we have it already?
					if (repository.getTransactionRepository().exists(transactionData.getSignature())) {
						LOGGER.trace(String.format("Ignoring existing TRANSACTION %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					// Is it valid?
					ValidationResult validationResult = transaction.isValidUnconfirmed();
					if (validationResult != ValidationResult.OK) {
						LOGGER.trace(String.format("Ignoring invalid (%s) TRANSACTION %s from peer %s", validationResult.name(), Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					// Seems ok - add to unconfirmed pile
					repository.getTransactionRepository().save(transactionData);
					repository.getTransactionRepository().unconfirmTransaction(transactionData);
					repository.saveChanges();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while responding to %s from peer %s", message.getType().name(), peer), e);
				}
				break;

			case GET_BLOCK_SUMMARIES:
				try (final Repository repository = RepositoryManager.getRepository()) {
					GetBlockSummariesMessage getBlockSummariesMessage = (GetBlockSummariesMessage) message;
					byte[] parentSignature = getBlockSummariesMessage.getParentSignature();

					List<BlockSummaryData> blockSummaries = new ArrayList<>();

					int numberRequested = Math.min(Network.MAX_BLOCK_SUMMARIES_PER_REPLY, getBlockSummariesMessage.getNumberRequested());

					do {
						BlockData blockData = repository.getBlockRepository().fromReference(parentSignature);

						if (blockData == null)
							break;

						BlockSummaryData blockSummary = new BlockSummaryData(blockData);
						blockSummaries.add(blockSummary);
						parentSignature = blockData.getSignature();
					} while (blockSummaries.size() < numberRequested);

					Message blockSummariesMessage = new BlockSummariesMessage(blockSummaries);
					blockSummariesMessage.setId(message.getId());
					if (!peer.sendMessage(blockSummariesMessage))
						peer.disconnect();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while responding to %s from peer %s", message.getType().name(), peer), e);
				}
				break;

			default:
				break;
		}
	}

	public void onNewTransaction(TransactionData transactionData) {
		// Send round to all peers
		Network.getInstance().broadcast(peer -> new TransactionMessage(transactionData));
	}

}
