package org.qora.controller;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
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
import org.qora.crypto.Crypto;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockSummaryData;
import org.qora.data.network.PeerData;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.ArbitraryTransactionData.DataType;
import org.qora.globalization.Translator;
import org.qora.data.transaction.TransactionData;
import org.qora.gui.Gui;
import org.qora.gui.SysTray;
import org.qora.network.Network;
import org.qora.network.Peer;
import org.qora.network.message.ArbitraryDataMessage;
import org.qora.network.message.BlockMessage;
import org.qora.network.message.BlockSummariesMessage;
import org.qora.network.message.GetArbitraryDataMessage;
import org.qora.network.message.GetBlockMessage;
import org.qora.network.message.GetBlockSummariesMessage;
import org.qora.network.message.GetPeersMessage;
import org.qora.network.message.GetSignaturesMessage;
import org.qora.network.message.GetSignaturesV2Message;
import org.qora.network.message.GetTransactionMessage;
import org.qora.network.message.GetUnconfirmedTransactionsMessage;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.HeightV2Message;
import org.qora.network.message.Message;
import org.qora.network.message.SignaturesMessage;
import org.qora.network.message.TransactionMessage;
import org.qora.network.message.TransactionSignaturesMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.settings.Settings;
import org.qora.transaction.ArbitraryTransaction;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transaction.Transaction.ValidationResult;
import org.qora.ui.UiService;
import org.qora.utils.Base58;
import org.qora.utils.NTP;
import org.qora.utils.Triple;

public class Controller extends Thread {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	/** Controller start-up time (ms) taken using <tt>System.currentTimeMillis()</tt>. */
	public static final long startTime = System.currentTimeMillis();
	public static final String VERSION_PREFIX = "qora-core-";

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);
	private static final long MISBEHAVIOUR_COOLOFF = 10 * 60 * 1000; // ms
	private static final int MAX_BLOCKCHAIN_TIP_AGE = 5; // blocks
	private static final Object shutdownLock = new Object();
	private static final String repositoryUrlTemplate = "jdbc:hsqldb:file:%s/blockchain;create=true;hsqldb.full_log_replay=true";
	private static final long ARBITRARY_REQUEST_TIMEOUT = 5 * 1000; // ms
	private static final long REPOSITORY_BACKUP_PERIOD = 123 * 60 * 1000; // ms
	private static final long NTP_CHECK_PERIOD = 10 * 60 * 1000; // ms
	private static final long MAX_NTP_OFFSET = 500; // ms

	private static volatile boolean isStopping = false;
	private static BlockGenerator blockGenerator = null;
	private static volatile boolean requestSync = false;
	private static volatile boolean requestSysTrayUpdate = false;
	private static Controller instance;

	private final String buildVersion;
	private final long buildTimestamp; // seconds

	private long repositoryBackupTimestamp = startTime + REPOSITORY_BACKUP_PERIOD;
	private long ntpCheckTimestamp = startTime; // ms
	/** Whether BlockGenerator is allowed to generate blocks. Mostly determined by system clock accuracy. */
	private volatile boolean isGenerationAllowed = false;

	/** Signature of peer's latest block when we tried to sync but peer had inferior chain. */
	private byte[] inferiorChainPeerBlockSignature = null;
	/** Signature of our latest block when we tried to sync but peer had inferior chain. */
	private byte[] inferiorChainOurBlockSignature = null;

	/**
	 * Map of recent requests for ARBITRARY transaction data payloads.
	 * <p>
	 * Key is original request's message ID<br>
	 * Value is Triple&lt;transaction signature in base58, first requesting peer, first request's timestamp&gt;
	 * <p>
	 * If peer is null then either:<br>
	 * <ul>
	 * <li>we are the original requesting peer</li>
	 * <li>we have already sent data payload to original requesting peer.</li>
	 * </ul>
	 * If signature is null then we have already received the data payload and either:<br>
	 * <ul>
	 * <li>we are the original requesting peer and have saved it locally</li>
	 * <li>we have forwarded the data payload (and maybe also saved it locally)</li>
	 * </ul>
	 */
	private Map<Integer, Triple<String, Peer, Long>> arbitraryDataRequests = Collections.synchronizedMap(new HashMap<>());

	/** Lock for only allowing one blockchain-modifying codepath at a time. e.g. synchronization or newly generated block. */
	private final ReentrantLock blockchainLock = new ReentrantLock();

	// Constructors

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

	public boolean isGenerationAllowed() {
		return this.isGenerationAllowed;
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

		LOGGER.info("Starting controller");
		Controller.getInstance().start();

		LOGGER.info("Starting networking on port " + Settings.getInstance().getListenPort());
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

		LOGGER.info("Starting block generator");
		blockGenerator = new BlockGenerator();
		blockGenerator.start();

		// Arbitrary transaction data manager
		// LOGGER.info("Starting arbitrary-transaction data manager");
		// ArbitraryDataManager.getInstance().start();

		// Auto-update service
		LOGGER.info("Starting auto-update");
		AutoUpdate.getInstance().start();

		LOGGER.info("Starting API on port " + Settings.getInstance().getApiPort());
		try {
			ApiService apiService = ApiService.getInstance();
			apiService.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start API", e);
			System.exit(1);
		}

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

	/** Called by AdvancedInstaller's launch EXE in single-instance mode, when an instance is already running. */
	public static void secondaryMain(String args[]) {
		// Return as we don't want to run more than one instance
	}


	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		try {
			while (!isStopping) {
				Thread.sleep(1000);

				if (requestSync) {
					requestSync = false;
					potentiallySynchronize();
				}

				// Clean up arbitrary data request cache
				final long requestMinimumTimestamp = System.currentTimeMillis() - ARBITRARY_REQUEST_TIMEOUT;
				arbitraryDataRequests.entrySet().removeIf(entry -> entry.getValue().getC() < requestMinimumTimestamp);

				// Give repository a chance to backup
				if (System.currentTimeMillis() >= repositoryBackupTimestamp) {
					repositoryBackupTimestamp += REPOSITORY_BACKUP_PERIOD;
					RepositoryManager.backup(true);
				}

				// Potentially nag end-user about NTP
				if (System.currentTimeMillis() >= ntpCheckTimestamp) {
					ntpCheckTimestamp += NTP_CHECK_PERIOD;
					isGenerationAllowed = ntpCheck();
					requestSysTrayUpdate = true;
				}

				// Prune stuck/slow/old peers
				try {
					Network.getInstance().prunePeers();
				} catch (DataException e) {
					LOGGER.warn(String.format("Repository issue when trying to prune peers: %s", e.getMessage()));
				}

				// Maybe update SysTray
				if (requestSysTrayUpdate) {
					requestSysTrayUpdate = false;
					updateSysTray();
				}
			}
		} catch (InterruptedException e) {
			// Fall-through to exit
		}
	}

	private void potentiallySynchronize() throws InterruptedException {
		List<Peer> peers = Network.getInstance().getUniqueHandshakedPeers();

		// Disregard peers that have "misbehaved" recently
		peers.removeIf(hasPeerMisbehaved);

		// Check we have enough peers to potentially synchronize
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return;

		// Disregard peers that don't have a recent block
		final long minLatestBlockTimestamp = getMinimumLatestBlockTimestamp();
		peers.removeIf(peer -> peer.getLastBlockTimestamp() == null || peer.getLastBlockTimestamp() < minLatestBlockTimestamp);

		BlockData latestBlockData = getChainTip();

		// Disregard peers that have no block signature or the same block signature as us
		peers.removeIf(peer -> peer.getLastBlockSignature() == null || Arrays.equals(latestBlockData.getSignature(), peer.getLastBlockSignature()));

		// Disregard peer we used last time, if both we and they are still on the same block and we didn't like their chain
		if (inferiorChainOurBlockSignature != null && Arrays.equals(inferiorChainOurBlockSignature, latestBlockData.getSignature()))
			peers.removeIf(peer -> Arrays.equals(inferiorChainPeerBlockSignature, peer.getLastBlockSignature()));

		if (!peers.isEmpty()) {
			// Pick random peer to sync with
			int index = new SecureRandom().nextInt(peers.size());
			Peer peer = peers.get(index);

			inferiorChainOurBlockSignature = null;
			inferiorChainPeerBlockSignature = null;

			SynchronizationResult syncResult = Synchronizer.getInstance().synchronize(peer, false);
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
					peerData.setLastMisbehaved(System.currentTimeMillis());

					// Only save to repository if outbound peer
					if (peer.isOutbound())
						try (final Repository repository = RepositoryManager.getRepository()) {
							repository.getNetworkRepository().save(peerData);
							repository.saveChanges();
						} catch (DataException e) {
							LOGGER.warn("Repository issue while updating peer synchronization info", e);
						}
					break;

				case INFERIOR_CHAIN:
					inferiorChainOurBlockSignature = latestBlockData.getSignature();
					inferiorChainPeerBlockSignature = peer.getLastBlockSignature();
					// These are minor failure results so fine to try again
					LOGGER.debug(() -> String.format("Refused to synchronize with peer %s (%s)", peer, syncResult.name()));
					break;

				case NO_REPLY:
				case NO_BLOCKCHAIN_LOCK:
				case REPOSITORY_ISSUE:
					// These are minor failure results so fine to try again
					LOGGER.debug(() -> String.format("Failed to synchronize with peer %s (%s)", peer, syncResult.name()));
					break;

				case OK:
					requestSysTrayUpdate = true;
					// fall-through...
				case NOTHING_TO_DO:
					LOGGER.debug(() -> String.format("Synchronized with peer %s (%s)", peer, syncResult.name()));
					break;
			}

			// Broadcast our new chain tip (if changed)
			BlockData newLatestBlockData = getChainTip();
			if (!Arrays.equals(newLatestBlockData.getSignature(), latestBlockData.getSignature()))
				Network.getInstance().broadcast(recipientPeer -> Network.getInstance().buildHeightMessage(recipientPeer, newLatestBlockData));
		}
	}

	/**
	 * Nag if we detect system clock is too far from internet time.
	 * 
	 * @return <tt>true</tt> if clock is accurate, <tt>false</tt> if inaccurate or we don't know.
	 */
	private boolean ntpCheck() {
		// Fetch mean offset from internet time (ms).
		Long meanOffset = NTP.getOffset();

		final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
		boolean isNtpActive = false;
		if (isWindows) {
			// Detecting Windows Time service

			String[] detectCmd = new String[] { "net", "start" };
			try {
				Process process = new ProcessBuilder(Arrays.asList(detectCmd)).start();
				try (InputStream in = process.getInputStream(); Scanner scanner = new Scanner(in, "UTF8")) {
					scanner.useDelimiter("\\A");
					String output = scanner.hasNext() ? scanner.next() : "";
					isNtpActive = output.contains("Windows Time");
				}
			} catch (IOException e) {
				// Not important
			}
		} else {
			// Very basic unix-based attempt to check for ntpd
			String[] detectCmd = new String[] { "ps", "-agx" };
			try {
				Process process = new ProcessBuilder(Arrays.asList(detectCmd)).start();
				try (InputStream in = process.getInputStream(); Scanner scanner = new Scanner(in, "UTF8")) {
					scanner.useDelimiter("\\A");
					String output = scanner.hasNext() ? scanner.next() : "";
					isNtpActive = output.contains("ntpd");
				}
			} catch (IOException e) {
				// Not important
			}
		}

		LOGGER.info(String.format("NTP mean offset %s, NTP service active: %s", meanOffset, isNtpActive));

		final boolean isOffsetGood = meanOffset != null && Math.abs(meanOffset) < MAX_NTP_OFFSET;

		// If offset bad or NTP not active then nag
		if (!isOffsetGood || !isNtpActive) {
			String caption = Translator.INSTANCE.translate("SysTray", "NTP_NAG_CAPTION");
			String text = Translator.INSTANCE.translate("SysTray", isWindows ? "NTP_NAG_TEXT_WINDOWS" : "NTP_NAG_TEXT_UNIX");
			SysTray.getInstance().showMessage(caption, text, MessageType.WARNING);
		}

		// Return whether we're accurate (disregarding whether NTP service is active)
		return isOffsetGood;
	}

	private void updateSysTray() {
		final int numberOfPeers = Network.getInstance().getUniqueHandshakedPeers().size();

		final int height = getChainHeight();

		String connectionsText = Translator.INSTANCE.translate("SysTray", numberOfPeers != 1 ? "CONNECTIONS" : "CONNECTION");
		String heightText = Translator.INSTANCE.translate("SysTray", "BLOCK_HEIGHT");
		String generatingText = Translator.INSTANCE.translate("SysTray", isGenerationAllowed ? "GENERATING_ENABLED" : "GENERATING_DISABLED");

		String tooltip = String.format("%s - %d %s - %s %d", generatingText, numberOfPeers, connectionsText, heightText, height);
		SysTray.getInstance().setToolTipText(tooltip);
	}

	// Shutdown

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down node management UI");
				UiService.getInstance().stop();

				LOGGER.info("Shutting down API");
				ApiService.getInstance().stop();

				LOGGER.info("Shutting down auto-update");
				AutoUpdate.getInstance().shutdown();

				// Arbitrary transaction data manager
				// LOGGER.info("Shutting down arbitrary-transaction data manager");
				// ArbitraryDataManager.getInstance().shutdown();

				if (blockGenerator != null) {
					LOGGER.info("Shutting down block generator");
					blockGenerator.shutdown();
					try {
						blockGenerator.join();
					} catch (InterruptedException e) {
						// We were interrupted while waiting for thread to join
					}
				}

				LOGGER.info("Shutting down networking");
				Network.getInstance().shutdown();

				LOGGER.info("Shutting down controller");
				this.interrupt();
				try {
					this.join();
				} catch (InterruptedException e) {
					// We were interrupted while waiting for thread to join
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

		// Send (if outbound) / Request peer lists
		network.broadcast(peer -> peer.isOutbound() ? network.buildPeersMessage(peer) : new GetPeersMessage());

		// Send our current height
		BlockData latestBlockData = getChainTip();
		network.broadcast(peer -> network.buildHeightMessage(peer, latestBlockData));

		// Send (if outbound) / Request unconfirmed transaction signatures
		network.broadcast(peer -> network.buildGetUnconfirmedTransactionsMessage(peer));
	}

	public void onGeneratedBlock() {
		// Broadcast our new height info
		BlockData latestBlockData = getChainTip();

		Network network = Network.getInstance();
		network.broadcast(peer -> network.buildHeightMessage(peer, latestBlockData));
	}

	public void onNewTransaction(TransactionData transactionData) {
		// Send round to all peers
		Network network = Network.getInstance();
		network.broadcast(peer -> network.buildNewTransactionMessage(peer, transactionData));
	}

	public void onPeerHandshakeCompleted(Peer peer) {
		// Only send if outbound
		if (peer.isOutbound()) {
			if (peer.getVersion() < 2) {
				// Legacy mode

				// Send our unconfirmed transactions
				try (final Repository repository = RepositoryManager.getRepository()) {
					List<TransactionData> transactions = repository.getTransactionRepository().getUnconfirmedTransactions();

					for (TransactionData transactionData : transactions) {
						Message transactionMessage = new TransactionMessage(transactionData);
						if (!peer.sendMessage(transactionMessage)) {
							peer.disconnect("failed to send unconfirmed transaction");
							return;
						}
					}
				} catch (DataException e) {
					LOGGER.error("Repository issue while sending unconfirmed transactions", e);
				}
			} else {
				// V2 protocol

				// Request peer's unconfirmed transactions
				Message message = new GetUnconfirmedTransactionsMessage();
				if (!peer.sendMessage(message)) {
					peer.disconnect("failed to send request for unconfirmed transactions");
					return;
				}
			}
		}

		requestSysTrayUpdate = true;
	}

	public void onPeerDisconnect(Peer peer) {
		requestSysTrayUpdate = true;
	}

	public void onNetworkMessage(Peer peer, Message message) {
		LOGGER.trace(() -> String.format("Processing %s message from %s", message.getType().name(), peer));

		switch (message.getType()) {
			case HEIGHT: {
				HeightMessage heightMessage = (HeightMessage) message;

				// Update all peers with same ID

				List<Peer> connectedPeers = Network.getInstance().getHandshakedPeers();
				for (Peer connectedPeer : connectedPeers) {
					if (connectedPeer.getPeerId() == null || !Arrays.equals(connectedPeer.getPeerId(), peer.getPeerId()))
						continue;

					connectedPeer.setLastHeight(heightMessage.getHeight());
				}

				// Potentially synchronize
				requestSync = true;

				break;
			}

			case HEIGHT_V2: {
				HeightV2Message heightV2Message = (HeightV2Message) message;

				// If peer is inbound and we've not updated their height
				// then this is probably their initial HEIGHT_V2 message
				// so they need a corresponding HEIGHT_V2 message from us
				if (!peer.isOutbound() && peer.getLastHeight() == null)
					peer.sendMessage(Network.getInstance().buildHeightMessage(peer, getChainTip()));

				// Update all peers with same ID

				List<Peer> connectedPeers = Network.getInstance().getHandshakedPeers();
				for (Peer connectedPeer : connectedPeers) {
					// Skip connectedPeer if they have no ID or their ID doesn't match sender's ID
					if (connectedPeer.getPeerId() == null || !Arrays.equals(connectedPeer.getPeerId(), peer.getPeerId()))
						continue;

					// We want to update atomically so use lock
					ReentrantLock peerLock = connectedPeer.getPeerDataLock();
					peerLock.lock();
					try {
						connectedPeer.setLastHeight(heightV2Message.getHeight());
						connectedPeer.setLastBlockSignature(heightV2Message.getSignature());
						connectedPeer.setLastBlockTimestamp(heightV2Message.getTimestamp());
						connectedPeer.setLastBlockGenerator(heightV2Message.getGenerator());
					} finally {
						peerLock.unlock();
					}
				}

				// Potentially synchronize
				requestSync = true;

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
						LOGGER.debug(() -> String.format("Ignoring GET_BLOCK request from peer %s for unknown block %s", peer, Base58.encode(signature)));
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
						LOGGER.debug(() -> String.format("Ignoring GET_TRANSACTION request from peer %s for unknown transaction %s", peer, Base58.encode(signature)));
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
						LOGGER.trace(() -> String.format("Ignoring %s transaction %s with invalid signature from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					ValidationResult validationResult = transaction.importAsUnconfirmed();

					if (validationResult == ValidationResult.TRANSACTION_ALREADY_EXISTS) {
						LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					if (validationResult == ValidationResult.NO_BLOCKCHAIN_LOCK) {
						LOGGER.trace(() -> String.format("Couldn't lock blockchain to import unconfirmed transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					if (validationResult != ValidationResult.OK) {
						LOGGER.trace(() -> String.format("Ignoring invalid (%s) %s transaction %s from peer %s", validationResult.name(), transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
						break;
					}

					LOGGER.debug(() -> String.format("Imported %s transaction %s from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while processing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer), e);
				}

				break;
			}

			case GET_UNCONFIRMED_TRANSACTIONS: {
				try (final Repository repository = RepositoryManager.getRepository()) {
					List<byte[]> signatures = repository.getTransactionRepository().getUnconfirmedTransactionSignatures();

					Message transactionSignaturesMessage = new TransactionSignaturesMessage(signatures);
					if (!peer.sendMessage(transactionSignaturesMessage))
						peer.disconnect("failed to send unconfirmed transaction signatures");
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while sending unconfirmed transaction signatures to peer %s", peer), e);
				}
				break;
			}

			case TRANSACTION_SIGNATURES: {
				TransactionSignaturesMessage transactionSignaturesMessage = (TransactionSignaturesMessage) message;
				List<byte[]> signatures = transactionSignaturesMessage.getSignatures();
				List<byte[]> newSignatures = new ArrayList<>();

				try (final Repository repository = RepositoryManager.getRepository()) {
					for (byte[] signature : signatures) {
						// Do we have it already? (Before requesting transaction data itself)
						if (repository.getTransactionRepository().exists(signature)) {
							LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(signature), peer));
							continue;
						}

						// Check isInterrupted() here and exit fast
						if (Thread.currentThread().isInterrupted())
							return;

						// Fetch actual transaction data from peer
						Message getTransactionMessage = new GetTransactionMessage(signature);
						Message responseMessage = peer.getResponse(getTransactionMessage);
						if (responseMessage == null || !(responseMessage instanceof TransactionMessage)) {
							// Maybe peer no longer has this transaction
							LOGGER.trace(() -> String.format("Peer %s didn't send transaction %s", peer, Base58.encode(signature)));
							continue;
						}

						// Check isInterrupted() here and exit fast
						if (Thread.currentThread().isInterrupted())
							return;

						TransactionMessage transactionMessage = (TransactionMessage) responseMessage;
						TransactionData transactionData = transactionMessage.getTransactionData();
						Transaction transaction = Transaction.fromData(repository, transactionData);

						// Check signature
						if (!transaction.isSignatureValid()) {
							LOGGER.trace(() -> String.format("Ignoring %s transaction %s with invalid signature from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
							continue;
						}

						ValidationResult validationResult = transaction.importAsUnconfirmed();

						if (validationResult == ValidationResult.TRANSACTION_ALREADY_EXISTS) {
							LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
							continue;
						}

						if (validationResult == ValidationResult.NO_BLOCKCHAIN_LOCK) {
							LOGGER.trace(() -> String.format("Couldn't lock blockchain to import unconfirmed transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
							// Some other thread (e.g. Synchronizer) might have blockchain lock for a while so might as well give up for now
							break;
						}

						if (validationResult != ValidationResult.OK) {
							LOGGER.trace(() -> String.format("Ignoring invalid (%s) %s transaction %s from peer %s", validationResult.name(), transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
							continue;
						}

						LOGGER.debug(() -> String.format("Imported %s transaction %s from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));

						// We could collate signatures that are new to us and broadcast them to our peers too
						newSignatures.add(signature);
					}
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while processing unconfirmed transactions from peer %s", peer), e);
				} catch (InterruptedException e) {
					// Shutdown
					return;
				}

				if (newSignatures.isEmpty())
					break;

				// Broadcast signatures that are new to us
				Network.getInstance().broadcast(broadcastPeer -> broadcastPeer == peer ? null : new TransactionSignaturesMessage(newSignatures));

				break;
			}

			case GET_BLOCK_SUMMARIES: {
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
			}

			case GET_ARBITRARY_DATA: {
				GetArbitraryDataMessage getArbitraryDataMessage = (GetArbitraryDataMessage) message;

				byte[] signature = getArbitraryDataMessage.getSignature();
				String signature58 = Base58.encode(signature);
				Long timestamp = System.currentTimeMillis();
				Triple<String, Peer, Long> newEntry = new Triple<>(signature58, peer, timestamp);

				// If we've seen this request recently, then ignore
				if (arbitraryDataRequests.putIfAbsent(message.getId(), newEntry) != null)
					break;

				// Do we even have this transaction?
				try (final Repository repository = RepositoryManager.getRepository()) {
					TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
					if (transactionData == null || transactionData.getType() != TransactionType.ARBITRARY)
						break;

					ArbitraryTransaction transaction = new ArbitraryTransaction(repository, transactionData);

					// If we have the data then send it
					if (transaction.isDataLocal()) {
						byte[] data = transaction.fetchData();
						if (data == null)
							break;

						// Update requests map to reflect that we've sent it
						newEntry = new Triple<>(signature58, null, timestamp);
						arbitraryDataRequests.put(message.getId(), newEntry);

						Message arbitraryDataMessage = new ArbitraryDataMessage(signature, data);
						arbitraryDataMessage.setId(message.getId());
						if (!peer.sendMessage(arbitraryDataMessage))
							peer.disconnect("failed to send arbitrary data");

						break;
					}

					// Ask our other peers if they have it
					Network.getInstance().broadcast(broadcastPeer -> broadcastPeer == peer ? null : message);
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while finding arbitrary transaction data for peer %s", peer), e);
				}

				break;
			}

			case ARBITRARY_DATA: {
				ArbitraryDataMessage arbitraryDataMessage = (ArbitraryDataMessage) message;

				// Do we have a pending request for this data?
				Triple<String, Peer, Long> request = arbitraryDataRequests.get(message.getId());
				if (request == null || request.getA() == null)
					break;

				// Does this message's signature match what we're expecting?
				byte[] signature = arbitraryDataMessage.getSignature();
				String signature58 = Base58.encode(signature);
				if (!request.getA().equals(signature58))
					break;

				byte[] data = arbitraryDataMessage.getData();

				// Check transaction exists and payload hash is correct
				try (final Repository repository = RepositoryManager.getRepository()) {
					TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
					if (transactionData == null || !(transactionData instanceof ArbitraryTransactionData))
						break;

					ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

					byte[] actualHash = Crypto.digest(data);

					// "data" from repository will always be hash of actual raw data
					if (!Arrays.equals(arbitraryTransactionData.getData(), actualHash))
						break;

					// Update requests map to reflect that we've received it
					Triple<String, Peer, Long> newEntry = new Triple<>(null, null, request.getC());
					arbitraryDataRequests.put(message.getId(), newEntry);

					// Save payload locally
					// TODO: storage policy
					arbitraryTransactionData.setDataType(DataType.RAW_DATA);
					arbitraryTransactionData.setData(data);
					repository.getArbitraryRepository().save(arbitraryTransactionData);
					repository.saveChanges();
				} catch (DataException e) {
					LOGGER.error(String.format("Repository issue while finding arbitrary transaction data for peer %s", peer), e);
				}

				Peer requestingPeer = request.getB();
				if (requestingPeer != null) {
					// Forward to requesting peer;
					if (!requestingPeer.sendMessage(arbitraryDataMessage))
						requestingPeer.disconnect("failed to forward arbitrary data");
				}

				break;
			}

			default:
				LOGGER.debug(String.format("Unhandled %s message [ID %d] from peer %s", message.getType().name(), message.getId(), peer));
				break;
		}
	}

	// Utilities

	public byte[] fetchArbitraryData(byte[] signature) throws InterruptedException {
		// Build request
		Message getArbitraryDataMessage = new GetArbitraryDataMessage(signature);

		// Save our request into requests map
		String signature58 = Base58.encode(signature);
		Triple<String, Peer, Long> requestEntry = new Triple<>(signature58, null, System.currentTimeMillis());

		// Assign random ID to this message
		int id;
		do {
			id = new Random().nextInt(Integer.MAX_VALUE - 1) + 1;

			// Put queue into map (keyed by message ID) so we can poll for a response
			// If putIfAbsent() doesn't return null, then this ID is already taken
		} while (arbitraryDataRequests.put(id, requestEntry) != null);
		getArbitraryDataMessage.setId(id);

		// Broadcast request
		Network.getInstance().broadcast(peer -> peer.getVersion() < 2 ? null : getArbitraryDataMessage);

		// Poll to see if data has arrived
		final long singleWait = 100;
		long totalWait = 0;
		while (totalWait < ARBITRARY_REQUEST_TIMEOUT) {
			Thread.sleep(singleWait);

			requestEntry = arbitraryDataRequests.get(id);
			if (requestEntry == null)
				return null;

			if (requestEntry.getA() == null)
				break;

			totalWait += singleWait;
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getArbitraryRepository().fetchData(signature);
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while fetching arbitrary transaction data"), e);
			return null;
		}
	}

	public static final Predicate<Peer> hasPeerMisbehaved = peer -> {
		Long lastMisbehaved = peer.getPeerData().getLastMisbehaved();
		return lastMisbehaved != null && lastMisbehaved > System.currentTimeMillis() - MISBEHAVIOUR_COOLOFF;
	};

	/** Returns whether we think our node has up-to-date blockchain based on our info about other peers. */
	public boolean isUpToDate() {
		final long minLatestBlockTimestamp = getMinimumLatestBlockTimestamp();
		BlockData latestBlockData = getChainTip();

		// Is our blockchain too old?
		if (latestBlockData.getTimestamp() < minLatestBlockTimestamp)
			return false;

		List<Peer> peers = Network.getInstance().getUniqueHandshakedPeers();

		// Disregard peers that have "misbehaved" recently
		peers.removeIf(hasPeerMisbehaved);

		// Check we have enough peers to potentially synchronize/generator
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return false;

		// Disregard peers that don't have a recent block
		peers.removeIf(peer -> peer.getLastBlockTimestamp() == null || peer.getLastBlockTimestamp() < minLatestBlockTimestamp);

		// If we don't have any peers left then can't synchronize, therefore consider ourself not up to date
		return !peers.isEmpty();
	}

	public static long getMinimumLatestBlockTimestamp() {
		return System.currentTimeMillis() - BlockChain.getInstance().getMaxBlockTime() * 1000L * MAX_BLOCKCHAIN_TIP_AGE;
	}

}
