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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.data.network.PeerData;
import org.qora.network.message.Message;
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

	private final boolean isOutbound;
	private Socket socket = null;
	private PeerData peerData = null;
	private Long connectionTimestamp = null;
	private OutputStream out;
	private Handshake handshakeStatus = Handshake.STARTED;
	private Map<Integer, BlockingQueue<Message>> messages;
	private VersionMessage versionMessage = null;
	private Integer version;
	private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private Long lastPing = null;
	private boolean isLocal;
	private byte[] peerId;

	/** Construct unconnected outbound Peer using socket address in peer data */
	public Peer(PeerData peerData) {
		this.isOutbound = true;
		this.peerData = peerData;
	}

	/** Construct Peer using existing, connected socket */
	public Peer(Socket socket) {
		this.isOutbound = false;
		this.socket = socket;

		this.isLocal = isAddressLocal(((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress());

		PeerAddress peerAddress = PeerAddress.fromSocket(socket);
		this.peerData = new PeerData(peerAddress);
	}

	// Getters / setters

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
			int index = Controller.VERSION_PREFIX.length();
			try {
				this.version = Integer.parseInt(this.versionMessage.getVersionString().substring(index, index + 1));
			} catch (NumberFormatException e) {
				this.version = 1;
			}
		} else {
			this.version = 1;
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

	public boolean getIsLocal() {
		return this.isLocal;
	}

	public byte[] getPeerId() {
		return this.peerId;
	}

	public void setPeerId(byte[] peerId) {
		this.peerId = peerId;
	}

	// Easier, and nicer output, than peer.getRemoteSocketAddress()

	@Override
	public String toString() {
		return this.peerData.getAddress().toString();
	}

	// Processing

	private void setup() throws IOException {
		this.socket.setSoTimeout(INACTIVITY_TIMEOUT);
		this.out = this.socket.getOutputStream();
		this.connectionTimestamp = NTP.getTime();
		this.messages = Collections.synchronizedMap(new HashMap<Integer, BlockingQueue<Message>>());
	}

	public boolean connect() {
		LOGGER.trace(String.format("Connecting to peer %s", this));
		this.socket = new Socket();

		try {
			InetSocketAddress resolvedSocketAddress = this.peerData.getAddress().toSocketAddress();

			this.isLocal = isAddressLocal(resolvedSocketAddress.getAddress());

			this.socket.connect(resolvedSocketAddress, CONNECT_TIMEOUT);
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
				if (message == null)
					return;

				LOGGER.trace(String.format("Received %s message with ID %d from peer %s", message.getType().name(), message.getId(), this));

				// Find potential blocking queue for this id (expect null if id is -1)
				BlockingQueue<Message> queue = this.messages.get(message.getId());
				if (queue != null) {
					// Adding message to queue will unblock thread waiting for response
					this.messages.get(message.getId()).add(message);
				} else {
					// Nothing waiting for this message - pass up to network
					Network.getInstance().onMessage(this, message);
				}
			}
		} catch (IOException e) {
			// Fall-through
		} finally {
			this.disconnect();
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
			id = new SecureRandom().nextInt(Integer.MAX_VALUE - 1) + 1;
			message.setId(id);

			// Put queue into map (keyed by message ID) so we can poll for a response
			// If putIfAbsent() doesn't return null, then this id is already taken
		} while (this.messages.putIfAbsent(id, blockingQueue) != null);

		// Try to send message
		if (!this.sendMessage(message)) {
			this.messages.remove(id);
			return null;
		}

		try {
			Message response = blockingQueue.poll(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);
			return response;
		} catch (InterruptedException e) {
			// Our thread was interrupted. Probably in shutdown scenario.
			return null;
		} finally {
			this.messages.remove(id);
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
				PingMessage pingMessage = new PingMessage();

				long before = System.currentTimeMillis();
				Message message = peer.getResponse(pingMessage);
				long after = System.currentTimeMillis();

				if (message == null || message.getType() != MessageType.PING)
					peer.disconnect();

				peer.setLastPing(after - before);
			}
		}
		;

		this.executor.scheduleWithFixedDelay(new Pinger(this), 0, PING_INTERVAL, TimeUnit.MILLISECONDS);
	}

	public void disconnect() {
		// Shut down pinger
		this.executor.shutdownNow();

		// Close socket
		if (!this.socket.isClosed()) {
			LOGGER.debug(String.format("Disconnecting peer %s", this));

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
