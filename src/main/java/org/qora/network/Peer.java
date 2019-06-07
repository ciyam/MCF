package org.qora.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.data.network.PeerData;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageException;
import org.qora.network.message.Message.MessageType;
import org.qora.settings.Settings;
import org.qora.network.message.PingMessage;
import org.qora.network.message.VersionMessage;
import org.qora.utils.NTP;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

// For managing one peer
public class Peer implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger(Peer.class);

	private static final int CONNECT_TIMEOUT = 1000; // ms
	private static final int RESPONSE_TIMEOUT = 5000; // ms
	private static final int PING_INTERVAL = 20000; // ms - just under every 30s is usually ideal to keep NAT mappings refreshed
	private static final int INACTIVITY_TIMEOUT = 30000; // ms
	private static final int UNSOLICITED_MESSAGE_QUEUE_CAPACITY = 10;

	private final boolean isOutbound;
	private Socket socket = null;
	private PeerData peerData = null;
	private final ReentrantLock peerDataLock = new ReentrantLock();
	private Long connectionTimestamp = null;
	private OutputStream out;
	private Handshake handshakeStatus = Handshake.STARTED;
	private Map<Integer, BlockingQueue<Message>> replyQueues;
	private BlockingQueue<Message> unsolicitedQueue;
	private ExecutorService messageExecutor;
	private VersionMessage versionMessage = null;
	private Integer version;
	private ScheduledExecutorService pingExecutor;
	private Long lastPing = null;
	private InetSocketAddress resolvedAddress = null;
	private boolean isLocal;
	private byte[] peerId;

	private byte[] pendingPeerId;
	private byte[] verificationCodeSent;
	private byte[] verificationCodeExpected;

	/** Construct unconnected outbound Peer using socket address in peer data */
	public Peer(PeerData peerData) {
		this.isOutbound = true;
		this.peerData = peerData;
	}

	/** Construct Peer using existing, connected socket */
	public Peer(Socket socket) {
		this.isOutbound = false;
		this.socket = socket;

		this.resolvedAddress = ((InetSocketAddress) socket.getRemoteSocketAddress());
		this.isLocal = isAddressLocal(this.resolvedAddress.getAddress());

		PeerAddress peerAddress = PeerAddress.fromSocket(socket);
		this.peerData = new PeerData(peerAddress);
	}

	// Getters / setters

	public PeerData getPeerData() {
		this.peerDataLock.lock();

		try {
			return this.peerData;
		} finally {
			this.peerDataLock.unlock();
		}
	}

	public boolean isOutbound() {
		return this.isOutbound;
	}

	public Handshake getHandshakeStatus() {
		return this.handshakeStatus;
	}

	public void setHandshakeStatus(Handshake handshakeStatus) {
		this.handshakeStatus = handshakeStatus;
	}

	public VersionMessage getVersionMessage() {
		return this.versionMessage;
	}

	public void setVersionMessage(VersionMessage versionMessage) {
		this.versionMessage = versionMessage;

		if (this.versionMessage.getVersionString().startsWith(Controller.VERSION_PREFIX)) {
			this.version = 2; // enhanced protocol
		} else {
			this.version = 1; // legacy protocol
		}
	}

	public Integer getVersion() {
		return this.version;
	}

	public Long getConnectionTimestamp() {
		return this.connectionTimestamp;
	}

	public Long getLastPing() {
		return this.lastPing;
	}

	public void setLastPing(long lastPing) {
		this.lastPing = lastPing;
	}

	public InetSocketAddress getResolvedAddress() {
		return this.resolvedAddress;
	}

	public boolean getIsLocal() {
		return this.isLocal;
	}

	public byte[] getPeerId() {
		return this.peerId;
	}

	public void setPeerId(byte[] peerId) {
		this.peerId = peerId;
	}

	public byte[] getPendingPeerId() {
		return this.pendingPeerId;
	}

	public void setPendingPeerId(byte[] peerId) {
		this.pendingPeerId = peerId;
	}

	public byte[] getVerificationCodeSent() {
		return this.verificationCodeSent;
	}

	public byte[] getVerificationCodeExpected() {
		return this.verificationCodeExpected;
	}

	public void setVerificationCodes(byte[] sent, byte[] expected) {
		this.verificationCodeSent = sent;
		this.verificationCodeExpected = expected;
	}

	/** Returns the lock used for synchronizing access to peer's PeerData. */
	public ReentrantLock getPeerDataLock() {
		return this.peerDataLock;
	}

	// Easier, and nicer output, than peer.getRemoteSocketAddress()

	@Override
	public String toString() {
		return this.peerData.getAddress().toString();
	}

	// Processing

	public void generateVerificationCodes() {
		verificationCodeSent = new byte[Network.PEER_ID_LENGTH];
		new SecureRandom().nextBytes(verificationCodeSent);

		verificationCodeExpected = new byte[Network.PEER_ID_LENGTH];
		new SecureRandom().nextBytes(verificationCodeExpected);
	}

	class MessageProcessor implements Runnable {
		private Peer peer;
		private BlockingQueue<Message> blockingQueue;

		public MessageProcessor(Peer peer, BlockingQueue<Message> blockingQueue) {
			this.peer = peer;
			this.blockingQueue = blockingQueue;
		}

		@Override
		public void run() {
			Thread.currentThread().setName("Peer UMP " + this.peer);

			while (true) {
				try {
					Message message = blockingQueue.poll(1000L, TimeUnit.MILLISECONDS);
					if (message != null)
						Network.getInstance().onMessage(peer, message);
				} catch (InterruptedException e) {
					// Shutdown
					return;
				}
			}
		}
	}

	private void setup() throws IOException {
		this.socket.setSoTimeout(INACTIVITY_TIMEOUT);
		this.out = this.socket.getOutputStream();
		this.connectionTimestamp = NTP.getTime();
		this.replyQueues = Collections.synchronizedMap(new HashMap<Integer, BlockingQueue<Message>>());

		this.unsolicitedQueue = new ArrayBlockingQueue<>(UNSOLICITED_MESSAGE_QUEUE_CAPACITY);
		this.messageExecutor = Executors.newSingleThreadExecutor();
		this.messageExecutor.execute(new MessageProcessor(this, this.unsolicitedQueue));
	}

	public boolean connect() {
		LOGGER.trace(String.format("Connecting to peer %s", this));
		this.socket = new Socket();

		try {
			this.resolvedAddress = this.peerData.getAddress().toSocketAddress();
			this.isLocal = isAddressLocal(this.resolvedAddress.getAddress());

			this.socket.connect(resolvedAddress, CONNECT_TIMEOUT);
			LOGGER.debug(String.format("Connected to peer %s", this));
		} catch (SocketTimeoutException e) {
			LOGGER.trace(String.format("Connection timed out to peer %s", this));
			return false;
		} catch (UnknownHostException e) {
			LOGGER.trace(String.format("Connection failed to unresolved peer %s", this));
			return false;
		} catch (IOException e) {
			LOGGER.trace(String.format("Connection failed to peer %s", this));
			return false;
		}

		return true;
	}

	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Peer " + this);

		try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
			setup();

			Network.getInstance().onPeerReady(this);

			while (true) {
				// Wait (up to INACTIVITY_TIMEOUT) for, and parse, incoming message
				Message message = Message.fromStream(in);
				if (message == null) {
					this.disconnect("null message");
					return;
				}

				LOGGER.trace(String.format("Received %s message with ID %d from peer %s", message.getType().name(), message.getId(), this));

				// Find potential blocking queue for this id (expect null if id is -1)
				BlockingQueue<Message> queue = this.replyQueues.get(message.getId());
				if (queue != null) {
					// Adding message to queue will unblock thread waiting for response
					this.replyQueues.get(message.getId()).add(message);
				} else {
					// Nothing waiting for this message (unsolicited) - queue up for network

					// Queue full?
					if (unsolicitedQueue.remainingCapacity() == 0) {
						LOGGER.debug(String.format("No room for %s message with ID %s from peer %s", message.getType().name(), message.getId(), this));
						continue;
					}

					unsolicitedQueue.add(message);
				}
			}
		} catch (MessageException e) {
			LOGGER.debug(String.format("%s, from peer %s", e.getMessage(), this));
			this.disconnect(e.getMessage());
		} catch (SocketTimeoutException e) {
			this.disconnect("timeout");
		} catch (IOException e) {
			this.disconnect("I/O error");
		} finally {
			Thread.currentThread().setName("disconnected peer");
		}
	}

	/**
	 * Attempt to send Message to peer
	 * 
	 * @param message
	 * @return <code>true</code> if message successfully sent; <code>false</code> otherwise
	 */
	public boolean sendMessage(Message message) {
		if (this.socket.isClosed())
			return false;

		try {
			// Send message
			LOGGER.trace(String.format("Sending %s message with ID %d to peer %s", message.getType().name(), message.getId(), this));

			synchronized (this.out) {
				this.out.write(message.toBytes());
				this.out.flush();
			}
		} catch (MessageException e) {
			LOGGER.warn(String.format("Failed to send %s message with ID %d to peer %s: %s", message.getType().name(), message.getId(), this, e.getMessage()));
		} catch (IOException e) {
			// Send failure
			return false;
		}

		// Sent OK
		return true;
	}

	/**
	 * Send message to peer and await response.
	 * <p>
	 * Message is assigned a random ID and sent. If a response with matching ID is received then it is returned to caller.
	 * <p>
	 * If no response with matching ID within timeout, or some other error/exception occurs, then return <code>null</code>. (Assume peer will be rapidly
	 * disconnected after this).
	 * 
	 * @param message
	 * @return <code>Message</code> if valid response received; <code>null</code> if not or error/exception occurs
	 */
	public Message getResponse(Message message) {
		BlockingQueue<Message> blockingQueue = new ArrayBlockingQueue<Message>(1);

		// Assign random ID to this message
		int id;
		do {
			id = new Random().nextInt(Integer.MAX_VALUE - 1) + 1;

			// Put queue into map (keyed by message ID) so we can poll for a response
			// If putIfAbsent() doesn't return null, then this ID is already taken
		} while (this.replyQueues.putIfAbsent(id, blockingQueue) != null);
		message.setId(id);

		// Try to send message
		if (!this.sendMessage(message)) {
			this.replyQueues.remove(id);
			return null;
		}

		try {
			Message response = blockingQueue.poll(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);
			return response;
		} catch (InterruptedException e) {
			// Our thread was interrupted. Probably in shutdown scenario.
			return null;
		} finally {
			this.replyQueues.remove(id);
		}
	}

	public void startPings() {
		class Pinger implements Runnable {
			private Peer peer;

			public Pinger(Peer peer) {
				this.peer = peer;
			}

			@Override
			public void run() {
				Thread.currentThread().setName("Pinger " + this.peer);

				PingMessage pingMessage = new PingMessage();

				long before = System.currentTimeMillis();
				Message message = peer.getResponse(pingMessage);
				long after = System.currentTimeMillis();

				if (message == null || message.getType() != MessageType.PING)
					peer.disconnect("no ping received");

				peer.setLastPing(after - before);
			}
		}

		Random random = new Random();
		long initialDelay = random.nextInt(PING_INTERVAL);
		this.pingExecutor = Executors.newSingleThreadScheduledExecutor();
		this.pingExecutor.scheduleWithFixedDelay(new Pinger(this), initialDelay, PING_INTERVAL, TimeUnit.MILLISECONDS);
	}

	public void disconnect(String reason) {
		LOGGER.trace(String.format("Disconnecting peer %s: %s", this, reason));

		// Shut down pinger
		if (this.pingExecutor != null) {
			this.pingExecutor.shutdownNow();
			this.pingExecutor = null;
		}

		// Shut down unsolicited message processor
		if (this.messageExecutor != null) {
			this.messageExecutor.shutdownNow();
			this.messageExecutor = null;
		}

		// Close socket
		if (!this.socket.isClosed()) {
			LOGGER.debug(String.format("Closing socket with peer %s: %s", this, reason));

			try {
				this.socket.close();
			} catch (IOException e) {
			}
		}

		Network.getInstance().onDisconnect(this);
	}

	// Utility methods

	/** Returns true if ports and addresses (or hostnames) match */
	public static boolean addressEquals(InetSocketAddress knownAddress, InetSocketAddress peerAddress) {
		if (knownAddress.getPort() != peerAddress.getPort())
			return false;

		return knownAddress.getHostString().equalsIgnoreCase(peerAddress.getHostString());
	}

	public static InetSocketAddress parsePeerAddress(String peerAddress) throws IllegalArgumentException {
		HostAndPort hostAndPort = HostAndPort.fromString(peerAddress).requireBracketsForIPv6();

		// HostAndPort doesn't try to validate host so we do extra checking here
		InetAddress address = InetAddresses.forString(hostAndPort.getHost());

		return new InetSocketAddress(address, hostAndPort.getPortOrDefault(Settings.DEFAULT_LISTEN_PORT));
	}

	public static boolean isAddressLocal(InetAddress address) {
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
	}

}
