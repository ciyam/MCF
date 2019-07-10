package org.qora.network;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.block.Block;
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
	/** Maximum time allowed for handshake to complete, in milliseconds. */
	private static final long HANDSHAKE_TIMEOUT = 60 * 1000; // ms

	/** Maximum message size (bytes). Needs to be at least maximum block size + MAGIC + message type, etc. */
	/* package */ static final int MAXIMUM_MESSAGE_SIZE = 4 + 1 + 4 + Block.MAX_BLOCK_BYTES;

	private static final byte[] MAINNET_MESSAGE_MAGIC = new byte[] { 0x51, 0x6d, 0x63, 0x66 }; // Qmcf
	private static final byte[] TESTNET_MESSAGE_MAGIC = new byte[] { 0x54, 0x6d, 0x63, 0x66 }; // Tmcf

	private static final String[] INITIAL_PEERS = new String[] {
			"node1.mcfamily.io",
			"node2.mcfamily.io",
			"node3.mcfamily.io",
			"node4.mcfamily.io",
			"node5.mcfamily.io",
			"node6.mcfamily.io",
			"node7.mcfamily.io",
			"47.52.103.13",
			"47.244.204.251",
			"183.3.205.197",
			"203.160.55.142",
			"47.56.55.89",
			"47.244.211.179"
	};

	public static final int MAX_SIGNATURES_PER_REPLY = 500;
	public static final int MAX_BLOCK_SUMMARIES_PER_REPLY = 500;
	public static final int PEER_ID_LENGTH = 128;

	private final byte[] ourPeerId;
	private volatile boolean isStopping = false;
	private List<Peer> connectedPeers;
	private List<PeerAddress> selfPeers;

	private ExecutorService networkingExecutor;
	private static Selector channelSelector;
	private static ServerSocketChannel serverChannel;
	private static AtomicBoolean isIterationInProgress = new AtomicBoolean(false);
	private static Iterator<SelectionKey> channelIterator = null;
	private static volatile boolean hasThreadPending = false;
	private static AtomicInteger activeThreads = new AtomicInteger(0);
	private static AtomicBoolean generalTaskLock = new AtomicBoolean(false);

	private int minOutboundPeers;
	private int maxPeers;
	private ExecutorService broadcastExecutor;
	/** Timestamp (ms) for next general info broadcast to all connected peers. Based on <tt>System.currentTimeMillis()</tt>. */
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

			channelSelector = Selector.open();

			// Set up listen socket
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			serverChannel.bind(endpoint, LISTEN_BACKLOG);
			serverChannel.register(channelSelector, SelectionKey.OP_ACCEPT);
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

		broadcastExecutor = Executors.newCachedThreadPool();
		nextBroadcast = System.currentTimeMillis();

		mergePeersLock = new ReentrantLock();

		// Start up first networking thread
		networkingExecutor = Executors.newCachedThreadPool();
		networkingExecutor.execute(new NetworkProcessor());
	}

	// Getters / setters

	public static Network getInstance() {
		if (instance == null)
			instance = new Network();

		return instance;
	}

	public byte[] getMessageMagic() {
		return Settings.getInstance().isTestNet() ? TESTNET_MESSAGE_MAGIC : MAINNET_MESSAGE_MAGIC;
	}

	public byte[] getOurPeerId() {
		return this.ourPeerId;
	}

	// Peer lists

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

	// Initial setup

	public static void installInitialPeers(Repository repository) throws DataException {
		for (String address : INITIAL_PEERS) {
			PeerAddress peerAddress = PeerAddress.fromString(address);

			PeerData peerData = new PeerData(peerAddress, System.currentTimeMillis(), "INIT");
			repository.getNetworkRepository().save(peerData);
		}

		repository.saveChanges();
	}

	// Main thread

	class NetworkProcessor implements Runnable {
		@Override
		public void run() {
			Thread.currentThread().setName("Network");

			activeThreads.incrementAndGet();
			LOGGER.trace(() -> String.format("Network thread %s, hasThreadPending: %s, activeThreads now: %d", Thread.currentThread().getId(), (hasThreadPending ? "yes" : "no"), activeThreads.get()));
			hasThreadPending = false;

			// Maintain long-term connections to various peers' API applications
			try {
				while (!isStopping) {
					if (!isIterationInProgress.compareAndSet(false, true)) {
						LOGGER.trace(() -> String.format("Network thread %s NOT producing (some other thread is) - exiting", Thread.currentThread().getId()));
						break;
					}

					LOGGER.trace(() -> String.format("Network thread %s is producing...", Thread.currentThread().getId()));

					final SelectionKey nextSelectionKey;
					try {
						// anything to do?
						if (channelIterator == null) {
							channelSelector.select(1000L);

							if (Thread.currentThread().isInterrupted())
								break;

							channelIterator = channelSelector.selectedKeys().iterator();
						}

						if (channelIterator.hasNext()) {
							nextSelectionKey = channelIterator.next();
							channelIterator.remove();
						} else {
							nextSelectionKey = null;
							channelIterator = null; // Nothing to do so reset iterator to cause new select
						}

						LOGGER.trace(() -> String.format("Network thread %s produced %s, iterator now %s",
								Thread.currentThread().getId(),
								(nextSelectionKey == null ? "null" : nextSelectionKey.channel()),
								(channelIterator == null ? "null" : channelIterator.toString())));

						// Spawn another thread in case we need help
						if (!hasThreadPending) {
							hasThreadPending = true;
							LOGGER.trace(() -> String.format("Network thread %s spawning", Thread.currentThread().getId()));
							networkingExecutor.execute(this);
						}
					} finally {
						LOGGER.trace(() -> String.format("Network thread %s done producing", Thread.currentThread().getId()));
						isIterationInProgress.set(false);
					}

					// process
					if (nextSelectionKey == null) {
						// no pending tasks, but we're last remaining thread so maybe connect a new peer or do a broadcast
						LOGGER.trace(() -> String.format("Network thread %s has no pending tasks", Thread.currentThread().getId()));

						if (!generalTaskLock.compareAndSet(false, true))
							continue;

						try {
							LOGGER.trace(() -> String.format("Network thread %s performing general tasks", Thread.currentThread().getId()));

							pingPeers();

							prunePeers();

							createConnection();

							if (System.currentTimeMillis() >= nextBroadcast) {
								nextBroadcast = System.currentTimeMillis() + BROADCAST_INTERVAL;

								// Controller can decide what to broadcast
								Controller.getInstance().doNetworkBroadcast();
							}
						} finally {
							LOGGER.trace(() -> String.format("Network thread %s finished general tasks", Thread.currentThread().getId()));
							generalTaskLock.set(false);
						}
					} else {
						try {
							LOGGER.trace(() -> String.format("Network thread %s has pending channel: %s, with ops %d",
									Thread.currentThread().getId(), nextSelectionKey.channel(), nextSelectionKey.readyOps()));

							// process pending channel task
							if (nextSelectionKey.isReadable()) {
								connectionRead((SocketChannel) nextSelectionKey.channel());
							} else if (nextSelectionKey.isAcceptable()) {
								acceptConnection((ServerSocketChannel) nextSelectionKey.channel());
							}

							LOGGER.trace(() -> String.format("Network thread %s processed channel: %s", Thread.currentThread().getId(), nextSelectionKey.channel()));
						} catch (CancelledKeyException e) {
							LOGGER.trace(() -> String.format("Network thread %s encountered cancelled channel: %s", Thread.currentThread().getId(), nextSelectionKey.channel()));
						}
					}
				}
			} catch (InterruptedException e) {
				// Fall-through to shutdown
			} catch (DataException e) {
				LOGGER.warn("Repository issue while running network", e);
				// Fall-through to shutdown
			} catch (IOException e) {
				// Fall-through to shutdown
			} finally {
				activeThreads.decrementAndGet();
				LOGGER.trace(() -> String.format("Network thread %s ending, activeThreads now: %d", Thread.currentThread().getId(), activeThreads.get()));
				Thread.currentThread().setName("Network (dormant)");
			}
		}
	}

	private void acceptConnection(ServerSocketChannel serverSocketChannel) throws InterruptedException {
		SocketChannel socketChannel;

		try {
			socketChannel = serverSocketChannel.accept();
		} catch (IOException e) {
			return;
		}

		// No connection actually accepted?
		if (socketChannel == null)
			return;

		Peer newPeer;

		try {
			synchronized (this.connectedPeers) {
				if (connectedPeers.size() >= maxPeers) {
					// We have enough peers
					LOGGER.trace(String.format("Connection discarded from peer %s", socketChannel.getRemoteAddress()));
					return;
				}

				LOGGER.debug(String.format("Connection accepted from peer %s", socketChannel.getRemoteAddress()));

				newPeer = new Peer(socketChannel);
				this.connectedPeers.add(newPeer);
			}
		} catch (IOException e) {
			if (socketChannel.isOpen())
				try {
					socketChannel.close();
				} catch (IOException ce) {
				}

			return;
		}

		try {
			socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
			socketChannel.configureBlocking(false);
			socketChannel.register(channelSelector, SelectionKey.OP_READ);
		} catch (IOException e) {
			// Remove from connected peers
			synchronized (this.connectedPeers) {
				this.connectedPeers.remove(newPeer);
			}

			return;
		}

		this.onPeerReady(newPeer);
	}

	private void pingPeers() {
		for (Peer peer : this.getConnectedPeers())
			peer.pingCheck();
	}

	private void prunePeers() throws InterruptedException, DataException {
		final long now = System.currentTimeMillis();

		// Disconnect peers that are stuck during handshake
		List<Peer> handshakePeers = this.getConnectedPeers();

		// Disregard peers that have completed handshake or only connected recently
		handshakePeers.removeIf(peer -> peer.getHandshakeStatus() == Handshake.COMPLETED || peer.getConnectionTimestamp() == null || peer.getConnectionTimestamp() > now - HANDSHAKE_TIMEOUT);

		for (Peer peer : handshakePeers)
			peer.disconnect("handshake timeout");

		// Prune 'old' peers from repository...
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Fetch all known peers
			List<PeerData> peers = repository.getNetworkRepository().getAllPeers();

			// 'Old' peers:
			// we have attempted to connect within the last day
			// we last managed to connect over a week ago
			Predicate<PeerData> isNotOldPeer = peerData -> {
				if (peerData.getLastAttempted() == null || peerData.getLastAttempted() < now - OLD_PEER_ATTEMPTED_PERIOD)
					return true;

				if (peerData.getLastConnected() == null || peerData.getLastConnected() > now - OLD_PEER_CONNECTION_PERIOD)
					return true;

				return false;
			};

			// Disregard peers that are NOT 'old'
			peers.removeIf(isNotOldPeer);

			// Don't consider already connected peers (simple address match)
			Predicate<PeerData> isConnectedPeer = peerData -> {
				PeerAddress peerAddress = peerData.getAddress();
				return this.connectedPeers.stream().anyMatch(peer -> peer.getPeerData().getAddress().equals(peerAddress));
			};

			synchronized (this.connectedPeers) {
				peers.removeIf(isConnectedPeer);
			}

			for (PeerData peerData : peers) {
				LOGGER.debug(String.format("Deleting old peer %s from repository", peerData.getAddress().toString()));
				repository.getNetworkRepository().delete(peerData.getAddress());
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
			final long lastAttemptedThreshold = System.currentTimeMillis() - CONNECT_FAILURE_BACKOFF;
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
			peerData.setLastAttempted(System.currentTimeMillis());
			repository.getNetworkRepository().save(peerData);
			repository.saveChanges();
		}

		SocketChannel socketChannel = newPeer.connect();
		if (socketChannel == null)
			return;

		if (this.isInterrupted())
			return;

		synchronized (this.connectedPeers) {
			this.connectedPeers.add(newPeer);
		}

		try {
			socketChannel.register(channelSelector, SelectionKey.OP_READ);
		} catch (ClosedChannelException e) {
			// If channel has somehow already closed then remove from connectedPeers
			synchronized (this.connectedPeers) {
				this.connectedPeers.remove(newPeer);
			}
		}

		this.onPeerReady(newPeer);
	}

	private void connectionRead(SocketChannel socketChannel) {
		Peer peer = getPeerFromChannel(socketChannel);
		if (peer == null)
			return;

		try {
			peer.readMessages();
		} catch (IOException e) {
			LOGGER.trace(() -> String.format("Network thread %s encountered I/O error: %s", Thread.currentThread().getId(), e.getMessage()), e);
			peer.disconnect("I/O error");
			return;
		}
	}

	private Peer getPeerFromChannel(SocketChannel socketChannel) {
		synchronized (this.connectedPeers) {
			for (Peer peer : this.connectedPeers)
				if (peer.getSocketChannel() == socketChannel)
					return peer;
		}

		return null;
	}

	// Peer callbacks

	/** Called when Peer's thread has setup and is ready to process messages */
	public void onPeerReady(Peer peer) {
		this.onMessage(peer, null);
	}

	public void onDisconnect(Peer peer) {
		// Notify Controller
		Controller.getInstance().onPeerDisconnect(peer);

		synchronized (this.connectedPeers) {
			this.connectedPeers.remove(peer);
		}

		// If this is an inbound peer then remove from known peers list
		// as remote port is not likely to be remote peer's listen port
		if (!peer.isOutbound())
			try (final Repository repository = RepositoryManager.getRepository()) {
				repository.getNetworkRepository().delete(peer.getPeerData().getAddress());
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

				mergePeers(peer.toString(), peerAddresses);
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

				mergePeers(peer.toString(), peerV2Addresses);
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
		peer.getPeerData().setLastConnected(System.currentTimeMillis());

		// Update connection info for outbound peers only
		if (peer.isOutbound())
			try (final Repository repository = RepositoryManager.getRepository()) {
				repository.getNetworkRepository().save(peer.getPeerData());
				repository.saveChanges();
			} catch (DataException e) {
				LOGGER.warn(String.format("Repository issue while trying to update outbound peer %s", peer));
			}

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

	// Message-building calls

	/** Returns PEERS message made from peers we've connected to recently, and this node's details */
	public Message buildPeersMessage(Peer peer) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

			// Filter out peers that we've not connected to ever or within X milliseconds
			final long connectionThreshold = System.currentTimeMillis() - RECENT_CONNECTION_THRESHOLD;
			Predicate<PeerData> notRecentlyConnected = peerData -> {
				final Long lastAttempted = peerData.getLastAttempted();
				final Long lastConnected = peerData.getLastConnected();

				if (lastAttempted == null || lastConnected == null)
					return true;

				if (lastConnected < lastAttempted)
					return true;

				if (lastConnected < connectionThreshold)
					return true;

				return false;
			};
			knownPeers.removeIf(notRecentlyConnected);

			if (peer.getVersion() >= 2) {
				List<PeerAddress> peerAddresses = new ArrayList<>();

				for (PeerData peerData : knownPeers) {
					try {
						InetAddress address = InetAddress.getByName(peerData.getAddress().getHost());

						// Don't send 'local' addresses if peer is not 'local'. e.g. don't send localhost:9889 to node4.mcfamily.io
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

						// Don't send 'local' addresses if peer is not 'local'. e.g. don't send localhost:9889 to node4.qora.org
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

	// Peer-management calls

	public void noteToSelf(Peer peer) {
		LOGGER.info(String.format("No longer considering peer address %s as it connects to self", peer));

		synchronized (this.selfPeers) {
			this.selfPeers.add(peer.getPeerData().getAddress());
		}
	}

	public boolean forgetPeer(PeerAddress peerAddress) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			int numDeleted = repository.getNetworkRepository().delete(peerAddress);
			repository.saveChanges();

			disconnectPeer(peerAddress);

			return numDeleted != 0;
		}
	}

	public int forgetAllPeers() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			int numDeleted = repository.getNetworkRepository().deleteAllPeers();
			repository.saveChanges();

			for (Peer peer : this.getConnectedPeers())
				peer.disconnect("to be forgotten");

			return numDeleted;
		}
	}

	private void disconnectPeer(PeerAddress peerAddress) {
		// Disconnect peer
		try {
			InetSocketAddress knownAddress = peerAddress.toSocketAddress();

			List<Peer> peers = this.getConnectedPeers();
			peers.removeIf(peer -> !Peer.addressEquals(knownAddress, peer.getResolvedAddress()));

			for (Peer peer : peers)
				peer.disconnect("to be forgotten");
		} catch (UnknownHostException e) {
			// Unknown host isn't going to match any of our connected peers so ignore
		}
	}

	// Network-wide calls

	private void mergePeers(String addedBy, List<PeerAddress> peerAddresses) {
		// Serialize using lock to prevent repository deadlocks
		if (!mergePeersLock.tryLock())
			return;

		final long addedWhen = System.currentTimeMillis();

		try {
			try (final Repository repository = RepositoryManager.getRepository()) {
				List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

				// Filter out duplicates
				Predicate<PeerAddress> isKnownAddress = peerAddress -> {
					return knownPeers.stream().anyMatch(knownPeerData -> knownPeerData.getAddress().equals(peerAddress));
				};

				peerAddresses.removeIf(isKnownAddress);

				repository.discardChanges();

				// Save the rest into database
				for (PeerAddress peerAddress : peerAddresses) {
					PeerData peerData = new PeerData(peerAddress, addedWhen, addedBy);
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

				Random random = new Random();

				for (Peer peer : targetPeers) {
					// Very short sleep to reduce strain, improve multi-threading and catch interrupts
					try {
						Thread.sleep(random.nextInt(20) + 20);
					} catch (InterruptedException e) {
						break;
					}

					Message message = peerMessageBuilder.apply(peer);

					if (message == null)
						continue;

					if (!peer.sendMessage(message))
						peer.disconnect("failed to broadcast message");
				}

				Thread.currentThread().setName("Network Broadcast (dormant)");
			}
		}

		try {
			broadcastExecutor.execute(new Broadcaster(this.getUniqueHandshakedPeers(), peerMessageBuilder));
		} catch (RejectedExecutionException e) {
			// Can't execute - probably because we're shutting down, so ignore
		}
	}

	// Shutdown

	public void shutdown() {
		this.isStopping = true;

		// Close listen socket to prevent more incoming connections
		if (serverChannel.isOpen())
			try {
				serverChannel.close();
			} catch (IOException e) {
				// Not important
			}

		// Stop processing threads
		this.networkingExecutor.shutdownNow();
		try {
			if (!this.networkingExecutor.awaitTermination(5000, TimeUnit.MILLISECONDS))
				LOGGER.debug("Network threads failed to terminate");
		} catch (InterruptedException e) {
			LOGGER.debug("Interrupted while waiting for networking threads to terminate");
		}

		// Stop broadcasts
		this.broadcastExecutor.shutdownNow();
		try {
			if (!this.broadcastExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS))
				LOGGER.debug("Broadcast threads failed to terminate");
		} catch (InterruptedException e) {
			LOGGER.debug("Interrupted while waiting for broadcast threads failed to terminate");
		}

		// Close all peer connections
		for (Peer peer : this.getConnectedPeers())
			peer.shutdown();
	}

}
