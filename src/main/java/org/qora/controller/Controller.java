package org.qora.controller;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.api.ApiService;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.controller.Synchronizer.SynchronizationResult;
import org.qora.data.block.BlockData;
import org.qora.data.network.BlockSummaryData;
import org.qora.data.network.PeerData;
import org.qora.data.transaction.TransactionData;
import org.qora.gui.Gui;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.BlockSummariesMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetBlockSummariesMessage;
import org.qora.network.message.GetPeersMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.GetSignaturesV2Message;
import org.qora.network.message.GetTransactionMessage;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.HeightV2Message;
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
import org.qora.ui.UiService;
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
	private static final long MISBEHAVIOUR_COOLOFF = 60 * 60 * 1000; // ms
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
		try (InputStream in = this.getClass().getResourceAsStream("/build.properties")) {
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

	/** Returns highest block, or null if there's a repository issue */
	public BlockData getChainTip() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getLastBlock();
		} catch (DataException e) {
			LOGGER.error("Repository issue when fetching blockchain tip", e);
			return null;
		}
	}

	public ReentrantLock getBlockchainLock() {
		return this.blockchainLock;
	}

	// Entry point

	public static void main(String args[]) {
		LOGGER.info("Starting up...");

		// Potential GUI startup with splash screen, etc.
		Gui.getInstance();

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
			LOGGER.info(String.format("Our chain height at start-up: %d", getInstance().getChainHeight()));
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

		LOGGER.info("Starting node management UI on port " + Settings.getInstance().getUiPort());
		try {
			UiService uiService = UiService.getInstance();
			uiService.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start node management UI", e);
			System.exit(1);
		}

		// If GUI is enabled, we're no longer starting up but actually running now
		Gui.getInstance().notifyRunning();
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		try {
			while (!isStopping) {
				Thread.sleep(14 * 1000);

				potentiallySynchronize();

				// Query random connections for unconfirmed transactions?
			}
		} catch (InterruptedException e) {
			// time to exit
			return;
		}
	}

	private void potentiallySynchronize() {
		List<Peer> peers = Network.getInstance().getUniqueHandshakedPeers();

		// Check we have enough peers to potentially synchronize
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return;

		for(Peer peer : peers)
			LOGGER.trace(String.format("Peer %s is at height %d", peer, peer.getPeerData().getLastHeight()));

		// Remove peers with unknown height, lower height or same height and same block signature (unless we don't have their block signature)
		peers.removeIf(hasShorterBlockchain());

		// Remove peers that have "misbehaved" recently
		peers.removeIf(hasPeerMisbehaved);

		if (!peers.isEmpty()) {
			int ourHeight = getChainHeight();

			// Pick random peer to sync with
			int index = new SecureRandom().nextInt(peers.size());
			Peer peer = peers.get(index);

			SynchronizationResult syncResult = Synchronizer.getInstance().synchronize(peer);
			switch (syncResult) {
				case GENESIS_ONLY:
				case NO_COMMON_BLOCK:
				case TOO_FAR_BEHIND:
				case TOO_DIVERGENT:
				case INVALID_DATA:
					// These are more serious results that warrant a cool-off
					LOGGER.info(String.format("Failed to synchronize with peer %s (%s) - cooling off", peer, syncResult.name()));

					// Don't use this peer again for a while
					PeerData peerData = peer.getPeerData();
					peerData.setLastMisbehaved(NTP.getTime());

					// Only save to repository if outbound peer
					if (peer.isOutbound())
						try (final Repository repository = RepositoryManager.getRepository()) {
							repository.getNetworkRepository().save(peerData);
							repository.saveChanges();
						} catch (DataException e) {
							LOGGER.warn("Repository issue while updating peer synchronization info", e);
						}
					break;

				case NO_REPLY:
				case INFERIOR_CHAIN:
				case NO_BLOCKCHAIN_LOCK:
				case REPOSITORY_ISSUE:
					// These are minor failure results so fine to try again
					LOGGER.info(String.format("Failed to synchronize with peer %s (%s)", peer, syncResult.name()));
					break;

				case OK:
				case NOTHING_TO_DO:
					LOGGER.debug(String.format("Synchronized with peer %s (%s)", peer, syncResult.name()));
					break;
			}

			// Broadcast our new height (if changed)
			BlockData latestBlockData = getChainTip();
			if (latestBlockData.getHeight() != ourHeight)
				Network.getInstance().broadcast(recipientPeer -> Network.getInstance().buildHeightMessage(recipientPeer, latestBlockData));
		}
	}

	// Shutdown

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down node management UI");
				UiService.getInstance().stop();

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
		BlockData latestBlockData = getChainTip();
		network.broadcast(peer -> network.buildHeightMessage(peer, latestBlockData));

		// Request peers lists
		network.broadcast(peer -> new GetPeersMessage());
	}

	public void onGeneratedBlock() {
		// Broadcast our new height info
		BlockData latestBlockData = getChainTip();

		Network network = Network.getInstance();
		network.broadcast(peer -> network.buildHeightMessage(peer, latestBlockData));
	}

	public void onNewTransaction(TransactionData transactionData) {
		// Send round to all peers
		Network.getInstance().broadcast(peer -> new TransactionMessage(transactionData));
	}

	public void onNetworkMessage(Peer peer, Message message) {
		LOGGER.trace(String.format("Processing %s message from %s", message.getType().name(), peer));

		switch (message.getType()) {
			case HEIGHT: {
				HeightMessage heightMessage = (HeightMessage) message;

				// Update our record of peer's height
				PeerData peerData = peer.getPeerData();
				peer.getPeerData().setLastHeight(heightMessage.getHeight());

				// Only save to repository if outbound peer
				if (peer.isOutbound())
					try (final Repository repository = RepositoryManager.getRepository()) {
						repository.getNetworkRepository().save(peerData);
						repository.saveChanges();
					} catch (DataException e) {
						LOGGER.error(String.format("Repository issue while updating height of peer %s", peer), e);
					}

				break;
			}

			case HEIGHT_V2: {
				HeightV2Message heightV2Message = (HeightV2Message) message;

				// Update our record for peer's blockchain info
				PeerData peerData = peer.getPeerData();

				// We want to update atomically so use lock
				ReentrantLock peerDataLock = peer.getPeerDataLock();
				peerDataLock.lock();
				try {
					peerData.setLastHeight(heightV2Message.getHeight());
					peerData.setLastBlockSignature(heightV2Message.getSignature());
					peerData.setLastBlockTimestamp(heightV2Message.getTimestamp());
					peerData.setLastBlockGenerator(heightV2Message.getGenerator());
				} finally {
					peerDataLock.unlock();
				}

				// Only save to repository if outbound peer
				if (peer.isOutbound())
					try (final Repository repository = RepositoryManager.getRepository()) {
						repository.getNetworkRepository().save(peerData);
						repository.saveChanges();
					} catch (DataException e) {
						LOGGER.error(String.format("Repository issue while updating info of peer %s", peer), e);
					}

				break;
			}

			case GET_SIGNATURES: {
				GetSignaturesMessage getSignaturesMessage = (GetSignaturesMessage) message;
				byte[] parentSignature = getSignaturesMessage.getParentSignature();

				try (final Repository repository = RepositoryManager.getRepository()) {
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
						peer.disconnect("failed to send signatures");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while sending signatures after %s to peer %s", Base58.encode(parentSignature), peer), e);
				}
				break;
			}

			case GET_SIGNATURES_V2: {
				GetSignaturesV2Message getSignaturesMessage = (GetSignaturesV2Message) message;
				byte[] parentSignature = getSignaturesMessage.getParentSignature();

				try (final Repository repository = RepositoryManager.getRepository()) {
					List<byte[]> signatures = new ArrayList<>();

					do {
						BlockData blockData = repository.getBlockRepository().fromReference(parentSignature);

						if (blockData == null)
							break;

						parentSignature = blockData.getSignature();
						signatures.add(parentSignature);
					} while (signatures.size() < getSignaturesMessage.getNumberRequested());

					Message signaturesMessage = new SignaturesMessage(signatures);
					signaturesMessage.setId(message.getId());
					if (!peer.sendMessage(signaturesMessage))
						peer.disconnect("failed to send signatures (v2)");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while sending V2 signatures after %s to peer %s", Base58.encode(parentSignature), peer), e);
				}
				break;
			}

			case GET_BLOCK: {
				GetBlockMessage getBlockMessage = (GetBlockMessage) message;
				byte[] signature = getBlockMessage.getSignature();

				try (final Repository repository = RepositoryManager.getRepository()) {
					BlockData blockData = repository.getBlockRepository().fromSignature(signature);
					if (blockData == null) {
						LOGGER.debug(String.format("Ignoring GET_BLOCK request from peer %s for unknown block %s", peer, Base58.encode(signature)));
						// Send no response at all???
						break;
					}

					Block block = new Block(repository, blockData);

					Message blockMessage = new BlockMessage(block);
					blockMessage.setId(message.getId());
					if (!peer.sendMessage(blockMessage))
						peer.disconnect("failed to send block");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while send block %s to peer %s", Base58.encode(signature), peer), e);
				}
				break;
			}

			case GET_TRANSACTION: {
				GetTransactionMessage getTransactionMessage = (GetTransactionMessage) message;
				byte[] signature = getTransactionMessage.getSignature();

				try (final Repository repository = RepositoryManager.getRepository()) {
					TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
					if (transactionData == null) {
						LOGGER.debug(String.format("Ignoring GET_TRANSACTION request from peer %s for unknown transaction %s", peer, Base58.encode(signature)));
						// Send no response at all???
						break;
					}

					Message transactionMessage = new TransactionMessage(transactionData);
					transactionMessage.setId(message.getId());
					if (!peer.sendMessage(transactionMessage))
						peer.disconnect("failed to send transaction");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while send transaction %s to peer %s", Base58.encode(signature), peer), e);
				}
				break;
			}

			case TRANSACTION: {
				TransactionMessage transactionMessage = (TransactionMessage) message;
				TransactionData transactionData = transactionMessage.getTransactionData();

				try (final Repository repository = RepositoryManager.getRepository()) {
					Transaction transaction = Transaction.fromData(repository, transactionData);

					// Check signature
					if (!transaction.isSignatureValid()) {
						LOGGER.trace(String.format("Ignoring TRANSACTION %s with invalid signature from peer %s", Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					// Blockchain lock required to prevent multiple threads trying to save the same transaction simultaneously
					ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
					blockchainLock.lock();
					try {
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
						transaction.importAsUnconfirmed();
					} finally {
						blockchainLock.unlock();
					}
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while processing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer), e);
				}
				break;
			}

			case GET_BLOCK_SUMMARIES:
				GetBlockSummariesMessage getBlockSummariesMessage = (GetBlockSummariesMessage) message;
				byte[] parentSignature = getBlockSummariesMessage.getParentSignature();

				try (final Repository repository = RepositoryManager.getRepository()) {
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
						peer.disconnect("failed to send block summaries");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while sending block summaries after %s to peer %s", Base58.encode(parentSignature), peer), e);
				}
				break;

			default:
				LOGGER.debug(String.format("Unhandled %s message [ID %d] from peer %s", message.getType().name(), message.getId(), peer));
				break;
		}
	}

	// Utilities

	public static final Predicate<Peer> hasPeerMisbehaved = peer -> {
		Long lastMisbehaved = peer.getPeerData().getLastMisbehaved();
		return lastMisbehaved != null && lastMisbehaved > NTP.getTime() - MISBEHAVIOUR_COOLOFF;
	};

	/** True if peer has unknown height, lower height or same height and same block signature (unless we don't have their block signature). */
	public static Predicate<Peer> hasShorterBlockchain() {
		BlockData highestBlockData = getInstance().getChainTip();
		int ourHeight = highestBlockData.getHeight();

		return peer -> {
			PeerData peerData = peer.getPeerData();

			Integer peerHeight = peerData.getLastHeight();
			if (peerHeight == null || peerHeight < ourHeight)
				return true;

			if (peerHeight > ourHeight || peerData.getLastBlockSignature() == null)
				return false;

			// Remove if signatures match
			return Arrays.equals(peerData.getLastBlockSignature(), highestBlockData.getSignature());
		};
	}

	/** Returns whether we think our node has up-to-date blockchain based on our height info about other peers. */
	public boolean isUpToDate() {
		List<Peer> peers = Network.getInstance().getUniqueHandshakedPeers();

		// Check we have enough peers to potentially synchronize/generator
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return false;

		// Remove peers with unknown height, lower height or same height and same block signature (unless we don't have their block signature)
		peers.removeIf(hasShorterBlockchain());

		// Remove peers that within 1 block of our height (actually ourHeight + 1)
		final int maxHeight = getChainHeight() + 1;
		peers.removeIf(peer -> peer.getPeerData().getLastHeight() <= maxHeight );

		// Remove peers that have "misbehaved" recently
		peers.removeIf(hasPeerMisbehaved);

		for (Peer peer : peers)
			LOGGER.debug(String.format("Not up to date due to peer %s at height %d with block sig %s", peer, peer.getPeerData().getLastHeight(), Base58.encode(peer.getPeerData().getLastBlockSignature())));

		// If we have any peers left, then they would be candidates for synchronization therefore we're not up to date.
		return peers.isEmpty();
	}

}
