package org.qora.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.data.network.PeerData;
import org.qora.data.transaction.TransactionData;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.Message;
import org.qora.network.message.PeersMessage;
import org.qora.network.message.PeersV2Message;
import org.qora.network.message.PingMessage;
import org.qora.network.message.TransactionMessage;
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

	public static final int PEER_ID_LENGTH = 128;

	private final byte[] ourPeerId;
	private List<Peer> connectedPeers;
	private List<PeerData> selfPeers;
	private ServerSocket listenSocket;
	private int minPeers;
	private int maxPeers;
	private ExecutorService peerExecutor;
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

		minPeers = Settings.getInstance().getMinPeers();
		maxPeers = Settings.getInstance().getMaxPeers();

		peerExecutor = Executors.newCachedThreadPool();
		nextBroadcast = System.currentTimeMillis();

		mergePeersLock = new ReentrantLock();
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

	public List<PeerData> getSelfPeers() {
		synchronized (this.selfPeers) {
			return new ArrayList<>(this.selfPeers);
		}
	}

	public void noteToSelf(Peer peer) {
		LOGGER.info(String.format("No longer considering peer address %s as it connects to self", peer));

		synchronized (this.selfPeers) {
			this.selfPeers.add(peer.getPeerData());
		}
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Network");

		// Maintain long-term connections to various peers' API applications
		try {
			while (true) {
				acceptConnections();

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

	private void createConnection() throws InterruptedException, DataException {
		synchronized (this.connectedPeers) {
			if (connectedPeers.size() >= minPeers)
				return;
		}

		Peer newPeer;

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Find an address to connect to
			List<PeerData> peers = repository.getNetworkRepository().getAllPeers();

			// Don't consider peers with recent connection failures
			final long lastAttemptedThreshold = NTP.getTime() - CONNECT_FAILURE_BACKOFF;
			peers.removeIf(peerData -> peerData.getLastAttempted() != null && peerData.getLastAttempted() > lastAttemptedThreshold);

			// Don't consider peers that we know loop back to ourself
			Predicate<PeerData> hasSamePeerSocketAddress = peerData -> this.selfPeers.stream()
					.anyMatch(selfPeerData -> selfPeerData.getSocketAddress().equals(peerData.getSocketAddress()));

			synchronized (this.selfPeers) {
				peers.removeIf(hasSamePeerSocketAddress);
			}

			// Don't consider already connected peers
			Predicate<PeerData> isConnectedPeer = peerData -> this.connectedPeers.stream()
					.anyMatch(peer -> peer.getPeerData().getSocketAddress().equals(peerData.getSocketAddress()));

			synchronized (this.connectedPeers) {
				peers.removeIf(isConnectedPeer);
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
				LOGGER.debug(String.format("Unexpected %s message from %s, expected %s", message.getType().name(), peer, handshakeStatus.expectedMessageType));
				peer.disconnect();
				return;
			}

			Handshake newHandshakeStatus = handshakeStatus.onMessage(peer, message);

			if (newHandshakeStatus == null) {
				// Handshake failure
				LOGGER.debug(String.format("Handshake failure with peer %s message %s", peer, message.getType().name()));
				peer.disconnect();
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
			case VERSION:
			case PEER_ID:
			case PROOF:
				LOGGER.debug(String.format("Unexpected handshaking message %s from peer %s", message.getType().name(), peer));
				peer.disconnect();
				return;

			case PING:
				PingMessage pingMessage = (PingMessage) message;

				// Generate 'pong' using same ID
				PingMessage pongMessage = new PingMessage();
				pongMessage.setId(pingMessage.getId());

				if (!peer.sendMessage(pongMessage))
					peer.disconnect();

				break;

			case PEERS:
				PeersMessage peersMessage = (PeersMessage) message;

				List<InetSocketAddress> peerAddresses = new ArrayList<>();

				// v1 PEERS message doesn't support port numbers so we have to add default port
				for (InetAddress peerAddress : peersMessage.getPeerAddresses())
					peerAddresses.add(new InetSocketAddress(peerAddress, Settings.DEFAULT_LISTEN_PORT));

				// Also add peer's details
				peerAddresses.add(new InetSocketAddress(peer.getRemoteSocketAddress().getHostString(), Settings.DEFAULT_LISTEN_PORT));

				mergePeers(peerAddresses);
				break;

			case PEERS_V2:
				PeersV2Message peersV2Message = (PeersV2Message) message;

				List<InetSocketAddress> peerV2Addresses = peersV2Message.getPeerAddresses();

				// First entry contains remote peer's listen port but empty address.
				// Overwrite address with one obtained from socket.
				int peerPort = peerV2Addresses.get(0).getPort();
				peerV2Addresses.remove(0);
				peerV2Addresses.add(0, InetSocketAddress.createUnresolved(peer.getRemoteSocketAddress().getHostString(), peerPort));

				mergePeers(peerV2Addresses);
				break;

			default:
				// Bump up to controller for possible action
				Controller.getInstance().onNetworkMessage(peer, message);
				break;
		}
	}

	private void onHandshakeCompleted(Peer peer) {
		// Make a note that we've successfully completed handshake (and when)
		peer.getPeerData().setLastConnected(NTP.getTime());

		// Start regular pings
		peer.startPings();

		// Send our height
		Message heightMessage = new HeightMessage(Controller.getInstance().getChainHeight());
		if (!peer.sendMessage(heightMessage)) {
			peer.disconnect();
			return;
		}

		// Send our peers list
		Message peersMessage = this.buildPeersMessage(peer);
		if (!peer.sendMessage(peersMessage))
			peer.disconnect();

		// Send our unconfirmed transactions
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<TransactionData> transactions = repository.getTransactionRepository().getUnconfirmedTransactions();

			for (TransactionData transactionData : transactions) {
				Message transactionMessage = new TransactionMessage(transactionData);
				if (!peer.sendMessage(transactionMessage)) {
					peer.disconnect();
					return;
				}
			}
		} catch (DataException e) {
			LOGGER.error("Repository issue while sending unconfirmed transactions", e);
		}
	}

	/** Returns PEERS message made from peers we've connected to recently, and this node's details */
	public Message buildPeersMessage(Peer peer) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

			// Filter out peers that we've not connected to ever or within X milliseconds
			long connectionThreshold = NTP.getTime() - RECENT_CONNECTION_THRESHOLD;
			knownPeers.removeIf(peerData -> peerData.getLastConnected() == null || peerData.getLastConnected() < connectionThreshold);

			// Map to socket addresses
			List<InetSocketAddress> peerSocketAddresses = knownPeers.stream().map(peerData -> peerData.getSocketAddress()).collect(Collectors.toList());

			if (peer.getVersion() >= 2)
				// New format PEERS_V2 message that supports hostnames, IPv6 and ports
				return new PeersV2Message(peerSocketAddresses);
			else
				// Legacy PEERS message that only sends IPv4 addresses
				return new PeersMessage(peerSocketAddresses);
		} catch (DataException e) {
			LOGGER.error("Repository issue while building PEERS message", e);
			return new PeersMessage(Collections.emptyList());
		}
	}

	// Network-wide calls

	/** Returns list of connected peers that have completed handshaking. */
	public List<Peer> getHandshakeCompletedPeers() {
		List<Peer> peers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			peers = this.connectedPeers.stream().filter(peer -> peer.getHandshakeStatus() == Handshake.COMPLETED).collect(Collectors.toList());
		}

		return peers;
	}

	/** Returns list of peers we connected to that have completed handshaking. */
	public List<Peer> getOutboundHandshakeCompletedPeers() {
		List<Peer> peers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			peers = this.connectedPeers.stream().filter(peer -> peer.isOutbound() && peer.getHandshakeStatus() == Handshake.COMPLETED)
					.collect(Collectors.toList());
		}

		return peers;
	}

	private void mergePeers(List<InetSocketAddress> peerAddresses) {
		mergePeersLock.lock();

		try {
			try (final Repository repository = RepositoryManager.getRepository()) {
				List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

				for (PeerData peerData : knownPeers)
					LOGGER.trace(String.format("Known peer %s", peerData.getSocketAddress()));

				// Resolve known peer hostnames
				Function<PeerData, InetSocketAddress> peerDataToSocketAddress = peerData -> new InetSocketAddress(peerData.getSocketAddress().getHostString(),
						peerData.getSocketAddress().getPort());
				List<InetSocketAddress> knownPeerAddresses = knownPeers.stream().map(peerDataToSocketAddress).collect(Collectors.toList());

				for (InetSocketAddress address : knownPeerAddresses)
					LOGGER.trace(String.format("Resolved known peer %s", address));

				// Filter out duplicates
				// We have to use our own Peer.addressEquals as InetSocketAddress.equals isn't quite right for us
				Predicate<InetSocketAddress> addressKnown = peerAddress -> knownPeerAddresses.stream()
						.anyMatch(knownAddress -> Peer.addressEquals(knownAddress, peerAddress));
				peerAddresses.removeIf(addressKnown);

				// Save the rest into database
				for (InetSocketAddress peerAddress : peerAddresses) {
					PeerData peerData = new PeerData(peerAddress);
					LOGGER.trace(String.format("Adding new peer %s to repository", peerAddress));
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

	public void broadcast(Function<Peer, Message> peerMessage) {
		class Broadcaster implements Runnable {
			private List<Peer> targetPeers;
			private Function<Peer, Message> peerMessage;

			public Broadcaster(List<Peer> targetPeers, Function<Peer, Message> peerMessage) {
				this.targetPeers = targetPeers;
				this.peerMessage = peerMessage;
			}

			@Override
			public void run() {
				for (Peer peer : targetPeers)
					if (!peer.sendMessage(peerMessage.apply(peer)))
						peer.disconnect();
			}
		}

		peerExecutor.execute(new Broadcaster(this.getHandshakeCompletedPeers(), peerMessage));
	}

	public void shutdown() {
		peerExecutor.shutdownNow();

		this.interrupt();
	}

}
