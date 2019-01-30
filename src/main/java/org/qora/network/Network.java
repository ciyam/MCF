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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.data.network.PeerData;
import org.qora.network.message.HeightMessage;
import org.qora.network.message.Message;
import org.qora.network.message.PeersMessage;
import org.qora.network.message.PingMessage;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.utils.NTP;

// For managing peers
public class Network extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(Network.class);
	private static final int LISTEN_BACKLOG = 10;
	private static final int CONNECT_FAILURE_BACKOFF = 60 * 1000; // ms
	private static final int BROADCAST_INTERVAL = 60 * 1000; // ms
	private static Network instance;

	public static final int PEER_ID_LENGTH = 128;

	private final byte[] ourPeerId;
	private List<Peer> connectedPeers;
	private List<PeerData> selfPeers;
	private ServerSocket listenSocket;
	private int minPeers;
	private int maxPeers;
	private ExecutorService peerExecutor;
	private long nextBroadcast;

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
		LOGGER.info(String.format("No longer considering peer address %s as it connects to self", peer.getRemoteSocketAddress()));

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
				acceptConnection();

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
	private void acceptConnection() throws InterruptedException {
		Socket socket;

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
					.anyMatch(peer -> peer.getPeerData() != null && peer.getPeerData().getSocketAddress().equals(peerData.getSocketAddress()));

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
			LOGGER.trace(String.format("Received %s message from %s", message.getType().name(), peer.getRemoteSocketAddress()));

		Handshake handshakeStatus = peer.getHandshakeStatus();
		if (handshakeStatus != Handshake.COMPLETED) {
			// Still handshaking

			// Check message type is as expected
			if (handshakeStatus.expectedMessageType != null && message.getType() != handshakeStatus.expectedMessageType) {
				LOGGER.debug(String.format("Unexpected %s message from %s, expected %s", message.getType().name(), peer.getRemoteSocketAddress(),
						handshakeStatus.expectedMessageType));
				peer.disconnect();
				return;
			}

			Handshake newHandshakeStatus = handshakeStatus.onMessage(peer, message);

			if (newHandshakeStatus == null) {
				// Handshake failure
				LOGGER.debug(String.format("Handshake failure with peer %s message %s", peer.getRemoteSocketAddress(), message.getType().name()));
				peer.disconnect();
				return;
			}

			if (peer.isOutbound())
				// If we made outbound connection then we need to act first
				newHandshakeStatus.action(peer);
			else
				// We have inbound connection so we need to respond inline with what we just received
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
				LOGGER.debug(String.format("Unexpected handshaking message %s from peer %s", message.getType().name(), peer.getRemoteSocketAddress()));
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

				for (InetAddress peerAddress : peersMessage.getPeerAddresses())
					peerAddresses.add(new InetSocketAddress(peerAddress, Settings.DEFAULT_LISTEN_PORT));

				try {
					mergePeers(peerAddresses);
				} catch (DataException e) {
					// Not good
					peer.disconnect();
					return;
				}
				break;

			default:
				// Bump up to controller for possible action
				Controller.getInstance().onNetworkMessage(peer, message);
				break;
		}
	}

	private void onHandshakeCompleted(Peer peer) {
		peer.startPings();

		Message heightMessage = new HeightMessage(Controller.getInstance().getChainHeight());

		if (!peer.sendMessage(heightMessage)) {
			peer.disconnect();
			return;
		}

		Message peersMessage = this.buildPeersMessage();
		if (!peer.sendMessage(peersMessage))
			peer.disconnect();
	}

	public Message buildPeersMessage() {
		List<Peer> peers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			// Only outbound peer connections that have completed handshake
			peers = this.connectedPeers.stream().filter(peer -> peer.isOutbound() && peer.getHandshakeStatus() == Handshake.COMPLETED)
					.collect(Collectors.toList());
		}

		return new PeersMessage(peers);
	}

	// Network-wide calls

	private List<Peer> getCompletedPeers() {
		List<Peer> completedPeers = new ArrayList<>();

		synchronized (this.connectedPeers) {
			completedPeers = this.connectedPeers.stream().filter(peer -> peer.getHandshakeStatus() == Handshake.COMPLETED).collect(Collectors.toList());
		}

		return completedPeers;
	}

	private void mergePeers(List<InetSocketAddress> peerAddresses) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<PeerData> knownPeers = repository.getNetworkRepository().getAllPeers();

			// Resolve known peer hostnames
			Function<PeerData, InetSocketAddress> peerDataToSocketAddress = peerData -> new InetSocketAddress(peerData.getSocketAddress().getHostString(),
					peerData.getSocketAddress().getPort());
			List<InetSocketAddress> knownPeerAddresses = knownPeers.stream().map(peerDataToSocketAddress).collect(Collectors.toList());

			// Filter out duplicates
			Predicate<InetSocketAddress> addressKnown = peerAddress -> knownPeerAddresses.stream().anyMatch(knownAddress -> knownAddress.equals(peerAddress));
			peerAddresses.removeIf(addressKnown);

			// Save the rest into database
			for (InetSocketAddress peerAddress : peerAddresses) {
				PeerData peerData = new PeerData(peerAddress);
				repository.getNetworkRepository().save(peerData);
			}

			repository.saveChanges();
		}
	}

	public void broadcast(Message message) {
		class Broadcaster implements Runnable {
			private List<Peer> targetPeers;
			private Message message;

			public Broadcaster(List<Peer> targetPeers, Message message) {
				this.targetPeers = targetPeers;
				this.message = message;
			}

			@Override
			public void run() {
				for (Peer peer : targetPeers)
					if (!peer.sendMessage(message))
						peer.disconnect();
			}
		}

		peerExecutor.execute(new Broadcaster(this.getCompletedPeers(), message));
	}

	public void shutdown() {
		peerExecutor.shutdownNow();

		this.interrupt();
	}

}
