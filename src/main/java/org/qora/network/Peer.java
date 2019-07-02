package org.qora.network;

import java.io.DataInputStream;
import java.io.EOFException;
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

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

// For managing one peer
public class Peer extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(Peer.class);

	/** Maximum time to allow <tt>connect()</tt> to remote peer to complete. (ms) */
	private static final int CONNECT_TIMEOUT = 1000; // ms

	/** Maximum time to wait for a message reply to arrive from peer. (ms) */
	private static final int RESPONSE_TIMEOUT = 5000; // ms

	/**
	 * Interval between PING messages to a peer. (ms)
	 * <p>
	 * Just under every 30s is usually ideal to keep NAT mappings refreshed,<br>
	 * BUT must be lower than {@link Peer#SOCKET_TIMEOUT}!
	 */
	private static final int PING_INTERVAL = 8000; // ms

	/** Maximum time a socket <tt>read()</tt> will block before closing connection due to timeout. (ms) */
	private static final int SOCKET_TIMEOUT = 10000; // ms

	private static final int UNSOLICITED_MESSAGE_QUEUE_CAPACITY = 10;

	private volatile boolean isStopping = false;

	private Socket socket = null;
	private InetSocketAddress resolvedAddress = null;
	/** True if remote address is loopback/link-local/site-local, false otherwise. */
	private boolean isLocal;
	private OutputStream out;

	private Map<Integer, BlockingQueue<Message>> replyQueues;

	private BlockingQueue<Message> unsolicitedQueue;
	private ExecutorService messageExecutor;

	private ScheduledExecutorService pingExecutor;

	/** True if we created connection to peer, false if we accepted incoming connection from peer. */
	private final boolean isOutbound;
	/** Numeric protocol version, typically 1 or 2. */
	private Integer version;
	private byte[] peerId;

	private Handshake handshakeStatus = Handshake.STARTED;

	private byte[] pendingPeerId;
	private byte[] verificationCodeSent;
	private byte[] verificationCodeExpected;

	private PeerData peerData = null;
	private final ReentrantLock peerLock = new ReentrantLock();

	/** Timestamp of when socket was accepted, or connected. */
	private Long connectionTimestamp = null;
	/** Version info as reported by peer. */
	private VersionMessage versionMessage = null;
	/** Last PING message round-trip time (ms). */
	private Long lastPing = null;
	/** Latest block height as reported by peer. */
	private Integer lastHeight;
	/** Latest block signature as reported by peer. */
	private byte[] lastBlockSignature;
	/** Latest block timestamp as reported by peer. */
	private Long lastBlockTimestamp;
	/** Latest block generator public key as reported by peer. */
	private byte[] lastBlockGenerator;

	// Constructors

	/** Construct unconnected, outbound Peer using socket address in peer data */
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

	public boolean isStopping() {
		return this.isStopping;
	}

	public PeerData getPeerData() {
		return this.peerData;
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

	public Integer getLastHeight() {
		return this.lastHeight;
	}

	public void setLastHeight(Integer lastHeight) {
		this.lastHeight = lastHeight;
	}

	public byte[] getLastBlockSignature() {
		return lastBlockSignature;
	}

	public void setLastBlockSignature(byte[] lastBlockSignature) {
		this.lastBlockSignature = lastBlockSignature;
	}

	public Long getLastBlockTimestamp() {
		return lastBlockTimestamp;
	}

	public void setLastBlockTimestamp(Long lastBlockTimestamp) {
		this.lastBlockTimestamp = lastBlockTimestamp;
	}

	public byte[] getLastBlockGenerator() {
		return lastBlockGenerator;
	}

	public void setLastBlockGenerator(byte[] lastBlockGenerator) {
		this.lastBlockGenerator = lastBlockGenerator;
	}

	/** Returns the lock used for synchronizing access to peer info. */
	public ReentrantLock getPeerLock() {
		return this.peerLock;
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
		this.socket.setSoTimeout(SOCKET_TIMEOUT);
		this.out = this.socket.getOutputStream();
		this.connectionTimestamp = System.currentTimeMillis();
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

			while (!isStopping) {
				// Wait (up to INACTIVITY_TIMEOUT) for, and parse, incoming message
				Message message = Message.fromStream(in);
				if (message == null) {
					this.disconnect("null message");
					return;
				}

				LOGGER.trace(() -> String.format("Received %s message with ID %d from peer %s", message.getType().name(), message.getId(), this));

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
			if (isStopping) {
				// If isStopping is true then our shutdown() has already been called, so no need to call it again
				LOGGER.debug(String.format("Peer %s stopping...", this));
				return;
			}

			// More informative logging
			if (e instanceof EOFException) {
				this.disconnect("EOF");
			} else if (e.getMessage().contains("onnection reset")) { // Can't import/rely on sun.net.ConnectionResetException
				this.disconnect("Connection reset");
			} else {
				this.disconnect("I/O error");
			}
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
			LOGGER.trace(() -> String.format("Sending %s message with ID %d to peer %s", message.getType().name(), message.getId(), this));

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
	 * If no response with matching ID within timeout, or some other error/exception occurs, then return <code>null</code>.<br>
	 * (Assume peer will be rapidly disconnected after this).
	 * 
	 * @param message
	 * @return <code>Message</code> if valid response received; <code>null</code> if not or error/exception occurs
	 * @throws InterruptedException
	 */
	public Message getResponse(Message message) throws InterruptedException {
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

				try {
					final long before = System.currentTimeMillis();
					Message message = peer.getResponse(pingMessage);
					final long after = System.currentTimeMillis();

					if (message == null || message.getType() != MessageType.PING)
						peer.disconnect("no ping received");

					peer.setLastPing(after - before);
				} catch (InterruptedException e) {
					// Shutdown
				}
			}
		}

		Random random = new Random();
		long initialDelay = random.nextInt(PING_INTERVAL);
		this.pingExecutor = Executors.newSingleThreadScheduledExecutor();
		this.pingExecutor.scheduleWithFixedDelay(new Pinger(this), initialDelay, PING_INTERVAL, TimeUnit.MILLISECONDS);
	}

	public void disconnect(String reason) {
		LOGGER.debug(String.format("Disconnecting peer %s: %s", this, reason));

		this.shutdown();

		Network.getInstance().onDisconnect(this);
	}

	public void shutdown() {
		LOGGER.debug(() -> String.format("Shutting down peer %s", this));
		this.isStopping = true;

		// Shut down pinger
		if (this.pingExecutor != null) {
			this.pingExecutor.shutdownNow();
			try {
				if (!this.pingExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS))
					LOGGER.debug(String.format("Pinger for peer %s failed to terminate", this));
			} catch (InterruptedException e) {
				LOGGER.debug(String.format("Interrupted while terminating pinger for peer %s", this));
			}
		}

		// Shut down unsolicited message processor
		if (this.messageExecutor != null) {
			this.messageExecutor.shutdownNow();
			try {
				if (!this.messageExecutor.awaitTermination(5000, TimeUnit.MILLISECONDS))
					LOGGER.debug(String.format("Message processor for peer %s failed to terminate", this));
			} catch (InterruptedException e) {
				LOGGER.debug(String.format("Interrupted while terminating message processor for peer %s", this));
			}
		}

		LOGGER.debug(() -> String.format("Interrupting peer %s", this));
		this.interrupt();

		// Close socket, which should trigger run() to exit
		if (!this.socket.isClosed()) {
			try {
				this.socket.close();
			} catch (IOException e) {
			}
		}

		try {
			this.join();
		} catch (InterruptedException e) {
			LOGGER.debug(String.format("Interrupted while waiting for peer %s to shutdown", this));
		}
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

	/** Returns true if address is loopback/link-local/site-local, false otherwise. */
	public static boolean isAddressLocal(InetAddress address) {
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
	}

}
