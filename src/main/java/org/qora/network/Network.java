package org.qora.network;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.data.block.BlockData;
import org.qora.data.network.PeerData;
import org.qora.data.transaction.TransactionData;
import org.qora.network.message.GetPeersMessage;
import org.qora.network.message.GetUnconfirmedTransactionsMessage;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.HeightV2Message;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageType;
import org.qora.network.message.PeerVerifyMessage;
import org.qora.network.message.PeersMessage;
import org.qora.network.message.PeersV2Message;
import org.qora.network.message.PingMessage;
import org.qora.network.message.TransactionMessage;
import org.qora.network.message.TransactionSignaturesMessage;
import org.qora.network.message.VerificationCodesMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.utils.NTP;

// For managing peers
public class Network extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(Network.class);
	private static Network instance;

	private static final int LISTEN_BACKLOG = 10;
	/** How long before retrying after a connection failure, in milliseconds. */
	private static final int CONNECT_FAILURE_BACKOFF = 60 * 1000; // ms
	/** How long between informational broadcasts to all connected peers, in milliseconds. */
	private static final int BROADCAST_INTERVAL = 60 * 1000; // ms
	/** Maximum time since last successful connection for peer info to be propagated, in milliseconds. */
	private static final long RECENT_CONNECTION_THRESHOLD = 24 * 60 * 60 * 1000; // ms
	/** Maximum time since last connection attempt before a peer is potentially considered "old", in milliseconds. */
	private static final long OLD_PEER_ATTEMPTED_PERIOD = 24 * 60 * 60 * 1000; // ms
	/** Maximum time since last successful connection before a peer is potentially considered "old", in milliseconds. */
	private static final long OLD_PEER_CONNECTION_PERIOD = 7 * 24 * 60 * 60 * 1000; // ms

	private static final String[] INITIAL_PEERS = new String[] {
			"node1.qora.org",
			"node2.qora.org",
			"node3.qora.org",
			"node4.qora.org",
			"node5.qora.org",
			"node6.qora.org",
			"node7.qora.org"
	};

	public static final int MAX_SIGNATURES_PER_REPLY = 500;
	public static final int MAX_BLOCK_SUMMARIES_PER_REPLY = 500;
	public static final int PEER_ID_LENGTH = 128;

	private final byte[] ourPeerId;
	private List<Peer> connectedPeers;
	private List<PeerAddress> selfPeers;
	private ServerSocket listenSocket;
	private int minOutboundPeers;
	private int maxPeers;
	private ExecutorService peerExecutor;
	private ExecutorService mergePeersExecutor;
	private long nextBroadcast;
	private Lock mergePeersLock;

	// Constructors

	private Network() {
		// Grab P2P port from settings
		int listenPort = Settings.getInstance().getListenPort();

		// Grab P2P bind address from settings
		try {
			InetAddress bindAddr = InetAddress.getByName(Settings.getInstance().getBindAddress());
			InetSocketAddress endpoint = new InetSocketAddress(bindAddr, listenPort);

			// Set up listen socket
			listenSocket = new ServerSocket();
			listenSocket.setReuseAddress(true);
			listenSocket.setSoTimeout(1); // accept() calls block for at most 1ms
			listenSocket.bind(endpoint, LISTEN_BACKLOG);
		} catch (UnknownHostException e) {
			LOGGER.error("Can't bind listen socket to address " + Settings.getInstance().getBindAddress());
			throw new RuntimeException("Can't bind listen socket to address");
		} catch (IOException e) {
			LOGGER.error("Can't create listen socket");
			throw new RuntimeException("Can't create listen socket");
		}

		connectedPeers = new ArrayList<>();
		selfPeers = new ArrayList<>();

		ourPeerId = new byte[PEER_ID_LENGTH];
		new SecureRandom().nextBytes(ourPeerId);

		minOutboundPeers = Settings.getInstance().getMinOutboundPeers();
		maxPeers = Settings.getInstance().getMaxPeers();

		peerExecutor = Executors.newCachedThreadPool();
		nextBroadcast = System.currentTimeMillis();

		mergePeersLock = new ReentrantLock();
		mergePeersExecutor = Executors.newCachedThreadPool();
	}

	// Getters / setters

	public static Network getInstance() {
		if (instance == null)
			instance = new Network();

		return instance;
	}

	public byte[] getOurPeerId() {
		return this.ourPeerId;
	}

	public List<Peer> getConnectedPeers() {
		synchronized (this.connectedPeers) {
			return new ArrayList<>(this.connectedPeers);
		}
	}

	public List<PeerAddress> getSelfPeers() {
		synchronized (this.selfPeers) {
			return new ArrayList<>(this.selfPeers);
		}
	}

	public void noteToSelf(Peer peer) {
		LOGGER.info(String.format("No longer considering peer address %s as it connects to self", peer));

		synchronized (this.selfPeers) {
			this.selfPeers.add(peer.getPeerData().getAddress());
		}
	}

	// Initial setup

	public static void installInitialPeers(Repository repository) throws DataException {
		for (String address : INITIAL_PEERS) {
			PeerAddress peerAddress = PeerAddress.fromString(address);

			PeerData peerData = new PeerData(peerAddress);
			repository.getNetworkRepository().save(peerData);
		}

		repository.saveChanges();
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Network");

		// Maintain long-term connections to various peers' API applications
		try {
			while (true) {
				acceptConnections();

				pruneOldPeers();

				createConnection();

				if (System.currentTimeMillis() >= this.nextBroadcast) {
					this.nextBroadcast = System.currentTimeMillis() + BROADCAST_INTERVAL;

					// Controller can decide what to broadcast
					Controller.getInstance().doNetworkBroadcast();
				}

				// Sleep for a while
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			// Fall-through to shutdown
		} catch (DataException e) {
			LOGGER.warn("Repository issue while running network", e);
			// Fall-through to shutdown
		}

		// Shutdown
		if (!this.listenSocket.isClosed())
			try {
				this.listenSocket.close();
			} catch (IOException e) {
				// Not important
			}
	}

	@SuppressWarnings("resource")
	private void acceptConnections() throws InterruptedException {
		Socket socket;

		do {
			try {
				socket = this.listenSocket.accept();
			} catch (SocketTimeoutException e) {
				// No connections to accept
				return;
			} catch (IOException e) {
				// Something went wrong or listen socket was closed due to shutdown
				return;
			}

			synchronized (this.connectedPeers) {
				if (connectedPeers.size() >= maxPeers) {
					// We have enough peers
					LOGGER.trace(String.format("Connection discarded from peer %s", socket.getRemoteSocketAddress()));

					try {
						socket.close();
					} catch (IOException e) {
						// Not important
					}

					return;
				}

				LOGGER.debug(String.format("Connection accepted from peer %s", socket.getRemoteSocketAddress()));
				Peer newPeer = new Peer(socket);
				this.connectedPeers.add(newPeer);
				peerExecutor.execute(newPeer);
			}
		} while (true);
	}

	private void pruneOldPeers() throws InterruptedException, DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Fetch all known peers
			List<PeerData> peers = repository.getNetworkRepository().getAllPeers();

			// "Old" peers:
			// we have attempted to connect within the last day
			// we last managed to connect over a week ago
			final long now = NTP.getTime();
			Predicate<PeerData> isNotOldPeer = peerData -> {
				if (peerData.getLastAttempted() == null || peerData.getLastAttempted() > now - OLD_PEER_ATTEMPTED_PERIOD)
					return true;

				if (peerData.getLastConnected() == null || peerData.getLastConnected() > now - OLD_PEER_CONNECTION_PERIOD)
					return true;

				return false;
			};

			peers.removeIf(isNotOldPeer);

			for (PeerData peerData : peers) {
				LOGGER.debug(String.format("Deleting old peer %s from repository", peerData.getAddress().toString()));
				repository.getNetworkRepository().delete(peerData);
			}

			repository.saveChanges();
		}
	}

	private void createConnection() throws InterruptedException, DataException {
		if (this.getOutboundHandshakedPeers().size() >= minOutboundPeers)
			return;

		Peer newPeer;

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Find an address to connect to
			List<PeerData> peers = repository.getNetworkRepository().getAllPeers();

			// Don't consider peers with recent connection failures
			final long lastAttemptedThreshold = NTP.getTime() - CONNECT_FAILURE_BACKOFF;
			peers.removeIf(peerData -> peerData.getLastAttempted() != null && peerData.getLastAttempted() > lastAttemptedThreshold);

			// Don't consider peers that we know loop back to ourself
			Predicate<PeerData> isSelfPeer = peerData -> {
				PeerAddress peerAddress = peerData.getAddress();
				return this.selfPeers.stream().anyMatch(selfPeer -> selfPeer.equals(peerAddress));
			};

			synchronized (this.selfPeers) {
				peers.removeIf(isSelfPeer);
			}

			// Don't consider already connected peers (simple address match)
			Predicate<PeerData> isConnectedPeer = peerData -> {
				PeerAddress peerAddress = peerData.getAddress();
				return this.connectedPeers.stream().anyMatch(peer -> peer.getPeerData().getAddress().equals(peerAddress));
			};

			synchronized (this.connectedPeers) {
				peers.removeIf(isConnectedPeer);
			}

			// Don't consider already connected peers (resolved address match)
			Predicate<PeerData> isResolvedAsConnectedPeer = peerData -> {
				try {
					InetSocketAddress resolvedSocketAddress = peerData.getAddress().toSocketAddress();
					return this.connectedPeers.stream().anyMatch(peer -> peer.getResolvedAddress().equals(resolvedSocketAddress));
				} catch (UnknownHostException e) {
					// Can't resolve - no point even trying to connect
					return true;
				}
			};

			synchronized (this.connectedPeers) {
				peers.removeIf(isResolvedAsConnectedPeer);
			}

			// Any left?
			if (peers.isEmpty())
				return;

			// Pick random peer
			int peerIndex = new SecureRandom().nextInt(peers.size());

			// Pick candidate
			PeerData peerData = peers.get(peerIndex);
			newPeer = new Peer(peerData);

			// Update connection attempt info
			repository.discardChanges();
			peerData.setLastAttempted(NTP.getTime());
			repository.getNetworkRepository().save(peerData);
			repository.saveChanges();
		}

		if (!newPeer.connect())
			return;

		synchronized (this.connectedPeers) {
			this.connectedPeers.add(newPeer);
		}

		peerExecutor.execute(newPeer);
	}

	// Peer callbacks

	/** Called when Peer's thread has setup and is ready to process messages */
	public void onPeerReady(Peer peer) {
		this.onMessage(peer, null);
	}

	public void onDisconnect(Peer peer) {
		synchronized (this.connectedPeers) {
			this.connectedPeers.remove(peer);
		}

		// If this is an inbound peer then remove from known peers list
		// as remote port is not likely to be remote peer's listen port
		if (!peer.isOutbound())
			try (final Repository repository = RepositoryManager.getRepository()) {
				repository.getNetworkRepository().delete(peer.getPeerData());
				repository.saveChanges();
			} catch (DataException e) {
				LOGGER.warn(String.format("Repository issue while trying to delete inbound peer %s", peer));
			}
	}

	/** Called when a new message arrives for a peer. message can be null if called after connection */
	public void onMessage(Peer peer, Message message) {
		if (message != null)
			LOGGER.trace(String.format("Received %s message from %s", message.getType().name(), peer));

		Handshake handshakeStatus = peer.getHandshakeStatus();
		if (handshakeStatus != Handshake.COMPLETED) {
			// Still handshaking

			// Check message type is as expected
			if (handshakeStatus.expectedMessageType != null && message.getType() != handshakeStatus.expectedMessageType) {
				// v1 nodes are keen on sending PINGs early. Discard as we'll send a PING right after handshake
				if (message.getType() == MessageType.PING)
					return;

				LOGGER.debug(String.format("Unexpected %s message from %s, expected %s", message.getType().name(), peer, handshakeStatus.expectedMessageType));
				peer.disconnect("unexpected message");
				return;
			}

			Handshake newHandshakeStatus = handshakeStatus.onMessage(peer, message);

			if (newHandshakeStatus == null) {
				// Handshake failure
				LOGGER.debug(String.format("Handshake failure with peer %s message %s", peer, message.getType().name()));
				peer.disconnect("handshake failure");
				return;
			}

			if (peer.isOutbound())
				// If we made outbound connection then we need to act first
				newHandshakeStatus.action(peer);
			else
				// We have inbound connection so we need to respond in kind with what we just received
				handshakeStatus.action(peer);

			peer.setHandshakeStatus(newHandshakeStatus);

			if (newHandshakeStatus == Handshake.COMPLETED)
				this.onHandshakeCompleted(peer);

			return;
		}

		// Should be non-handshaking messages from now on

		switch (message.getType()) {
			case PEER_VERIFY:
				// Remote peer wants extra verification
				possibleVerificationResponse(peer);
				break;

			case VERIFICATION_CODES:
				VerificationCodesMessage verificationCodesMessage = (VerificationCodesMessage) message;

				// Remote peer is sending the code it wants to receive back via our outbound connection to it
				Peer ourUnverifiedPeer = Network.getInstance().getInboundPeerWithId(Network.getInstance().getOurPeerId());
				ourUnverifiedPeer.setVerificationCodes(verificationCodesMessage.getVerificationCodeSent(), verificationCodesMessage.getVerificationCodeExpected());

				possibleVerificationResponse(ourUnverifiedPeer);
				break;

			case VERSION:
			case PEER_ID:
			case PROOF:
				LOGGER.debug(String.format("Unexpected handshaking message %s from peer %s", message.getType().name(), peer));
				peer.disconnect("unexpected handshaking message");
				return;

			case PING:
				PingMessage pingMessage = (PingMessage) message;

				// Generate 'pong' using same ID
				PingMessage pongMessage = new PingMessage();
				pongMessage.setId(pingMessage.getId());

				if (!peer.sendMessage(pongMessage))
					peer.disconnect("failed to send ping reply");

				break;

			case PEERS:
				PeersMessage peersMessage = (PeersMessage) message;

				List<PeerAddress> peerAddresses = new ArrayList<>();

				// v1 PEERS message doesn't support port numbers so we have to add default port
				for (InetAddress peerAddress : peersMessage.getPeerAddresses())
					// This is always IPv4 so we don't have to worry about bracketing IPv6.
					peerAddresses.add(PeerAddress.fromString(peerAddress.getHostAddress()));

				// Also add peer's details
				peerAddresses.add(PeerAddress.fromString(peer.getPeerData().getAddress().getHost()));

				mergePeers(peerAddresses);
				break;

			case PEERS_V2:
				PeersV2Message peersV2Message = (PeersV2Message) message;

				List<PeerAddress> peerV2Addresses = peersV2Message.getPeerAddresses();

				// First entry contains remote peer's listen port but empty address.
				int peerPort = peerV2Addresses.get(0).getPort();
				peerV2Addresses.remove(0);

				// If inbound peer, use listen port and socket address to recreate first entry
				if (!peer.isOutbound()) {
					PeerAddress sendingPeerAddress = PeerAddress.fromString(peer.getPeerData().getAddress().getHost() + ":" + peerPort);
					LOGGER.trace("PEERS_V2 sending peer's listen address: " + sendingPeerAddress.toString());
					peerV2Addresses.add(0, sendingPeerAddress);
				}

				mergePeers(peerV2Addresses);
				break;

			case GET_PEERS:
				// Send our known peers
				if (!peer.sendMessage(buildPeersMessage(peer)))
					peer.disconnect("failed to send peers list");
				break;

			default:
				// Bump up to controller for possible action
				Controller.getInstance().onNetworkMessage(peer, message);
				break;
		}
	}

	private void possibleVerificationResponse(Peer peer) {
		// Can't respond if we don't have the codes (yet?)
		if (peer.getVerificationCodeExpected() == null)
			return;

		PeerVerifyMessage peerVerifyMessage = new PeerVerifyMessage(peer.getVerificationCodeExpected());
		if (!peer.sendMessage(peerVerifyMessage)) {
			peer.disconnect("failed to send verification code");
			return;
		}

		peer.setVerificationCodes(null, null);
		peer.setHandshakeStatus(Handshake.COMPLETED);
		this.onHandshakeCompleted(peer);
	}

	private void onHandshakeCompleted(Peer peer) {
		// Do we need extra handshaking because of peer doppelgangers?
		if (peer.getPendingPeerId() != null) {
			peer.setHandshakeStatus(Handshake.PEER_VERIFY);
			peer.getHandshakeStatus().action(peer);
			return;
		}

		LOGGER.debug(String.format("Handshake completed with peer %s", peer));

		// Make a note that we've successfully completed handshake (and when)
		peer.getPeerData().setLastConnected(NTP.getTime());

		// Start regular pings
		peer.startPings();

		// Send our height
		Message heightMessage = buildHeightMessage(peer, Controller.getInstance().getChainTip());
		if (!peer.sendMessage(heightMessage)) {
			peer.disconnect("failed to send height/info");
			return;
		}

		// Send our peers list
		Message peersMessage = this.buildPeersMessage(peer);
		if (!peer.sendMessage(peersMessage))
			peer.disconnect("failed to send peers list");

		// Request their peers list
		Message getPeersMessage = new GetPeersMessage();
		if (!peer.sendMessage(getPeersMessage))
			peer.disconnect("failed to request peers list");

		// Ask Controller if they want to send anything
		Controller.getInstance().onPeerHandshakeCompleted(peer);
	}

	/** Returns PEERS message made from peers we've connected to recently, and this node's details */
	public Message buildPeersMessage(Peer peer) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

			// Filter out peers that we've not connected to ever or within X milliseconds
			long connectionThreshold = NTP.getTime() - RECENT_CONNECTION_THRESHOLD;
			knownPeers.removeIf(peerData -> peerData.getLastConnected() == null || peerData.getLastConnected() < connectionThreshold);

			if (peer.getVersion() >= 2) {
				List<PeerAddress> peerAddresses = new ArrayList<>();

				for (PeerData peerData : knownPeers) {
					try {
						InetAddress address = InetAddress.getByName(peerData.getAddress().getHost());

						// Don't send 'local' addresses if peer is not 'local'. e.g. don't send localhost:9084 to node4.qora.org
						if (!peer.getIsLocal() && Peer.isAddressLocal(address))
							continue;

						peerAddresses.add(peerData.getAddress());
					} catch (UnknownHostException e) {
						// Couldn't resolve hostname to IP address so discard
					}
				}

				// New format PEERS_V2 message that supports hostnames, IPv6 and ports
				return new PeersV2Message(peerAddresses);
			} else {
				// Map to socket addresses
				List<InetAddress> peerAddresses = new ArrayList<>();

				for (PeerData peerData : knownPeers) {
					try {
						// We have to resolve to literal IP address to check for IPv4-ness.
						// This isn't great if hostnames have both IPv6 and IPv4 DNS entries.
						InetAddress address = InetAddress.getByName(peerData.getAddress().getHost());

						// Legacy PEERS message doesn't support IPv6
						if (address instanceof Inet6Address)
							continue;

						// Don't send 'local' addresses if peer is not 'local'. e.g. don't send localhost:9084 to node4.qora.org
						if (!peer.getIsLocal() && !Peer.isAddressLocal(address))
							continue;

						peerAddresses.add(address);
					} catch (UnknownHostException e) {
						// Couldn't resolve hostname to IP address so discard
					}
				}

				// Legacy PEERS message that only sends IPv4 addresses
				return new PeersMessage(peerAddresses);
			}
		} catch (DataException e) {
			LOGGER.error("Repository issue while building PEERS message", e);
			return new PeersMessage(Collections.emptyList());
		}
	}

	public Message buildHeightMessage(Peer peer, BlockData blockData) {
		if (peer.getVersion() < 2) {
			// Legacy height message
			return new HeightMessage(blockData.getHeight());
		}

		// HEIGHT_V2 contains way more useful info
		return new HeightV2Message(blockData.getHeight(), blockData.getSignature(), blockData.getTimestamp(), blockData.getGeneratorPublicKey());
	}

	public Message buildNewTransactionMessage(Peer peer, TransactionData transactionData) {
		if (peer.getVersion() < 2) {
			// Legacy TRANSACTION message
			return new TransactionMessage(transactionData);
		}

		// In V2 we send out transaction signature only and peers can decide whether to request the full transaction
		return new TransactionSignaturesMessage(Collections.singletonList(transactionData.getSignature()));
	}

	public Message buildGetUnconfirmedTransactionsMessage(Peer peer) {
		// V2 only
		if (peer.getVersion() < 2)
			return null;

		return new GetUnconfirmedTransactionsMessage();
	}

	// Network-wide calls

	/** Returns list of connected peers that have completed handshaking. */
	public List<Peer> getHandshakedPeers() {
		List<Peer> peers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			peers = this.connectedPeers.stream().filter(peer -> peer.getHandshakeStatus() == Handshake.COMPLETED).collect(Collectors.toList());
		}

		return peers;
	}

	/** Returns list of connected peers that have completed handshaking, with inbound duplicates removed. */
	public List<Peer> getUniqueHandshakedPeers() {
		final List<Peer> peers;

		synchronized (this.connectedPeers) {
			peers = this.connectedPeers.stream().filter(peer -> peer.getHandshakeStatus() == Handshake.COMPLETED).collect(Collectors.toList());
		}

		// Returns true if this [inbound] peer has corresponding outbound peer with same ID
		Predicate<Peer> hasOutboundWithSameId = peer -> {
			// Peer is outbound so return fast
			if (peer.isOutbound())
				return false;

			return peers.stream().anyMatch(otherPeer -> otherPeer.isOutbound() && Arrays.equals(otherPeer.getPeerId(), peer.getPeerId()));
		};

		// Filter out [inbound] peers that have corresponding outbound peer with the same ID
		peers.removeIf(hasOutboundWithSameId);

		return peers;
	}

	/** Returns list of peers we connected to that have completed handshaking. */
	public List<Peer> getOutboundHandshakedPeers() {
		List<Peer> peers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			peers = this.connectedPeers.stream().filter(peer -> peer.isOutbound() && peer.getHandshakeStatus() == Handshake.COMPLETED)
					.collect(Collectors.toList());
		}

		return peers;
	}

	/** Returns Peer with inbound connection and matching ID, or null if none found. */
	public Peer getInboundPeerWithId(byte[] peerId) {
		synchronized (this.connectedPeers) {
			return this.connectedPeers.stream().filter(peer -> !peer.isOutbound() && peer.getPeerId() != null && Arrays.equals(peer.getPeerId(), peerId)).findAny().orElse(null);
		}
	}

	/** Returns handshake-completed Peer with outbound connection and matching ID, or null if none found. */
	public Peer getOutboundHandshakedPeerWithId(byte[] peerId) {
		synchronized (this.connectedPeers) {
			return this.connectedPeers.stream().filter(peer -> peer.isOutbound() && peer.getHandshakeStatus() == Handshake.COMPLETED && peer.getPeerId() != null && Arrays.equals(peer.getPeerId(), peerId)).findAny().orElse(null);
		}
	}

	private void mergePeers(List<PeerAddress> peerAddresses) {
		// This can block (due to lock) so fire off in separate thread
		class PeersMerger implements Runnable {
			private List<PeerAddress> peerAddresses;

			public PeersMerger(List<PeerAddress> peerAddresses) {
				this.peerAddresses = peerAddresses;
			}

			@Override
			public void run() {
				Thread.currentThread().setName("Merging peers");

				// Serialize using lock to prevent repository deadlocks
				mergePeersLock.lock();

				try {
					try (final Repository repository = RepositoryManager.getRepository()) {
						List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

						for (PeerData peerData : knownPeers)
							LOGGER.trace(String.format("Known peer %s", peerData.getAddress()));

						// Filter out duplicates
						Predicate<PeerAddress> isKnownAddress = peerAddress -> {
							return knownPeers.stream().anyMatch(knownPeerData -> knownPeerData.getAddress().equals(peerAddress));
						};

						peerAddresses.removeIf(isKnownAddress);

						// Save the rest into database
						for (PeerAddress peerAddress : peerAddresses) {
							PeerData peerData = new PeerData(peerAddress);
							LOGGER.info(String.format("Adding new peer %s to repository", peerAddress));
							repository.getNetworkRepository().save(peerData);
						}

						repository.saveChanges();
					} catch (DataException e) {
						LOGGER.error("Repository issue while merging peers list from remote node", e);
					}
				} finally {
					mergePeersLock.unlock();
				}
			}
		}

		mergePeersExecutor.execute(new PeersMerger(peerAddresses));
	}

	public void broadcast(Function<Peer, Message> peerMessageBuilder) {
		class Broadcaster implements Runnable {
			private List<Peer> targetPeers;
			private Function<Peer, Message> peerMessageBuilder;

			public Broadcaster(List<Peer> targetPeers, Function<Peer, Message> peerMessageBuilder) {
				this.targetPeers = targetPeers;
				this.peerMessageBuilder = peerMessageBuilder;
			}

			@Override
			public void run() {
				Thread.currentThread().setName("Network Broadcast");

				for (Peer peer : targetPeers) {
					Message message = peerMessageBuilder.apply(peer);

					if (message == null)
						continue;

					if (!peer.sendMessage(message))
						peer.disconnect("failed to broadcast message");
				}
			}
		}

		try {
			peerExecutor.execute(new Broadcaster(this.getUniqueHandshakedPeers(), peerMessageBuilder));
		} catch (RejectedExecutionException e) {
			// Can't execute - probably because we're shutting down, so ignore
		}
	}

	public void shutdown() {
		peerExecutor.shutdownNow();

		this.interrupt();
	}

}
