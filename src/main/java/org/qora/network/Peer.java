package org.qora.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.qora.utils.ExecuteProduceConsume;
import org.qora.utils.NTP;
import org.qora.network.message.PingMessage;
import org.qora.network.message.VersionMessage;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

// For managing one peer
public class Peer {

	private static final Logger LOGGER = LogManager.getLogger(Peer.class);

	/** Maximum time to allow <tt>connect()</tt> to remote peer to complete. (ms) */
	private static final int CONNECT_TIMEOUT = 1000; // ms

	/** Maximum time to wait for a message reply to arrive from peer. (ms) */
	private static final int RESPONSE_TIMEOUT = 5000; // ms

	/**
	 * Interval between PING messages to a peer. (ms)
	 * <p>
	 * Just under every 30s is usually ideal to keep NAT mappings refreshed.
	 */
	private static final int PING_INTERVAL = 8000; // ms

	private volatile boolean isStopping = false;

	private SocketChannel socketChannel = null;
	private InetSocketAddress resolvedAddress = null;
	/** True if remote address is loopback/link-local/site-local, false otherwise. */
	private boolean isLocal;
	private ByteBuffer byteBuffer;
	private Map<Integer, BlockingQueue<Message>> replyQueues;
	private LinkedBlockingQueue<Message> pendingMessages;

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
	private final ReentrantLock peerDataLock = new ReentrantLock();

	/** Timestamp of when socket was accepted, or connected. */
	private Long connectionTimestamp = null;

	/** Peer's value of connectionTimestamp. */
	private Long peersConnectionTimestamp = null;

	/** Version info as reported by peer. */
	private VersionMessage versionMessage = null;

	/** Last PING message round-trip time (ms). */
	private Long lastPing = null;
	/** When last PING message was sent, or null if pings not started yet. */
	private Long lastPingSent;

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
	public Peer(SocketChannel socketChannel) throws IOException {
		this.isOutbound = false;
		this.socketChannel = socketChannel;
		sharedSetup();

		this.resolvedAddress = ((InetSocketAddress) socketChannel.socket().getRemoteSocketAddress());
		this.isLocal = isAddressLocal(this.resolvedAddress.getAddress());

		PeerAddress peerAddress = PeerAddress.fromSocket(socketChannel.socket());
		this.peerData = new PeerData(peerAddress);
	}

	// Getters / setters

	public SocketChannel getSocketChannel() {
		return this.socketChannel;
	}

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

	public Long getPeersConnectionTimestamp() {
		return this.peersConnectionTimestamp;
	}

	/* package */ void setPeersConnectionTimestamp(Long peersConnectionTimestamp) {
		this.peersConnectionTimestamp = peersConnectionTimestamp;
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
	public ReentrantLock getPeerDataLock() {
		return this.peerDataLock;
	}

	@Override
	public String toString() {
		// Easier, and nicer output, than peer.getRemoteSocketAddress()
		return this.peerData.getAddress().toString();
	}

	// Processing

	public void generateVerificationCodes() {
		verificationCodeSent = new byte[Network.PEER_ID_LENGTH];
		new SecureRandom().nextBytes(verificationCodeSent);

		verificationCodeExpected = new byte[Network.PEER_ID_LENGTH];
		new SecureRandom().nextBytes(verificationCodeExpected);
	}

	private void sharedSetup() throws IOException {
		this.connectionTimestamp = NTP.getTime();
		this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
		this.socketChannel.configureBlocking(false);
		this.byteBuffer = ByteBuffer.allocate(Network.MAXIMUM_MESSAGE_SIZE);
		this.replyQueues = Collections.synchronizedMap(new HashMap<Integer, BlockingQueue<Message>>());
		this.pendingMessages = new LinkedBlockingQueue<Message>();
	}

	public SocketChannel connect() {
		LOGGER.trace(String.format("Connecting to peer %s", this));

		try {
			this.resolvedAddress = this.peerData.getAddress().toSocketAddress();
			this.isLocal = isAddressLocal(this.resolvedAddress.getAddress());

			this.socketChannel = SocketChannel.open();
			this.socketChannel.socket().connect(resolvedAddress, CONNECT_TIMEOUT);

			LOGGER.debug(String.format("Connected to peer %s", this));
			sharedSetup();
			return socketChannel;
		} catch (SocketTimeoutException e) {
			LOGGER.trace(String.format("Connection timed out to peer %s", this));
			return null;
		} catch (UnknownHostException e) {
			LOGGER.trace(String.format("Connection failed to unresolved peer %s", this));
			return null;
		} catch (IOException e) {
			LOGGER.trace(String.format("Connection failed to peer %s", this));
			return null;
		}
	}

	/**
	 * Attempt to buffer bytes from socketChannel.
	 * 
	 * @throws IOException
	 */
	/* package */ void readChannel() throws IOException {
		synchronized (this.byteBuffer) {
			if (!this.socketChannel.isOpen() || this.socketChannel.socket().isClosed())
				return;

			int bytesRead = this.socketChannel.read(this.byteBuffer);
			if (bytesRead == -1) {
				this.disconnect("EOF");
				return;
			}

			if (bytesRead == 0)
				// No room in buffer, or no more bytes to read
				return;

			LOGGER.trace(() -> String.format("Received %d bytes from peer %s", bytesRead, this));

			while (true) {
				final Message message;

				// Can we build a message from buffer now?
				try {
					message = Message.fromByteBuffer(this.byteBuffer);
				} catch (MessageException e) {
					LOGGER.debug(String.format("%s, from peer %s", e.getMessage(), this));
					this.disconnect(e.getMessage());
					return;
				}

				if (message == null)
					return;

				LOGGER.trace(() -> String.format("Received %s message with ID %d from peer %s", message.getType().name(), message.getId(), this));

				BlockingQueue<Message> queue = this.replyQueues.get(message.getId());
				if (queue != null) {
					// Adding message to queue will unblock thread waiting for response
					this.replyQueues.get(message.getId()).add(message);
					// Consumed elsewhere
					continue;
				}

				// No thread waiting for message so we need to pass it up to network layer

				// Add message to pending queue
				if (!this.pendingMessages.offer(message)) {
					LOGGER.info(String.format("No room to queue message from peer %s - discarding", this));
					return;
				}
			}
		}
	}

	/* package */ ExecuteProduceConsume.Task getMessageTask() {
		final Message nextMessage = this.pendingMessages.poll();

		if (nextMessage == null)
			return null;

		// Return a task to process message in queue
		return () -> Network.getInstance().onMessage(this, nextMessage);
	}

	/**
	 * Attempt to send Message to peer.
	 * 
	 * @param message
	 * @return <code>true</code> if message successfully sent; <code>false</code> otherwise
	 */
	public boolean sendMessage(Message message) {
		if (!this.socketChannel.isOpen())
			return false;

		try {
			// Send message
			LOGGER.trace(() -> String.format("Sending %s message with ID %d to peer %s", message.getType().name(), message.getId(), this));

			ByteBuffer outputBuffer = ByteBuffer.wrap(message.toBytes());

			synchronized (this.socketChannel) {
				while (outputBuffer.hasRemaining()) {
					int bytesWritten = this.socketChannel.write(outputBuffer);

					if (bytesWritten == 0)
						// Underlying socket's internal buffer probably full,
						// so wait a short while for bytes to actually be transmitted over the wire
						Thread.sleep(1L);
				}
			}
		} catch (MessageException e) {
			LOGGER.warn(String.format("Failed to send %s message with ID %d to peer %s: %s", message.getType().name(), message.getId(), this, e.getMessage()));
		} catch (IOException e) {
			// Send failure
			return false;
		} catch (InterruptedException e) {
			// Likely shutdown scenario - so exit
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

	/* package */ void startPings() {
		// Replacing initial null value allows pingCheck() to start sending pings.
		LOGGER.trace(() -> String.format("Enabling pings for peer %s", this));
		this.lastPingSent = System.currentTimeMillis();
	}

	/* package */ ExecuteProduceConsume.Task getPingTask() {
		// Pings not enabled yet?
		if (this.lastPingSent == null)
			return null;

		final long now = System.currentTimeMillis();

		// Time to send another ping?
		if (now < this.lastPingSent + PING_INTERVAL)
			return null; // Not yet

		// Not strictly true, but prevents this peer from being immediately chosen again
		this.lastPingSent = now;

		return () -> {
			PingMessage pingMessage = new PingMessage();
			Message message = this.getResponse(pingMessage);
			final long after = System.currentTimeMillis();

			if (message == null || message.getType() != MessageType.PING) {
				this.disconnect("no ping received");
				return;
			}

			this.setLastPing(after - now);
		};
	}

	public void disconnect(String reason) {
		if (!isStopping)
			LOGGER.debug(() -> String.format("Disconnecting peer %s: %s", this, reason));

		this.shutdown();

		Network.getInstance().onDisconnect(this);
	}

	public void shutdown() {
		if (!isStopping)
			LOGGER.debug(() -> String.format("Shutting down peer %s", this));

		isStopping = true;

		if (this.socketChannel.isOpen()) {
			try {
				this.socketChannel.shutdownOutput();
				this.socketChannel.close();
			} catch (IOException e) {
				LOGGER.debug(String.format("IOException while trying to close peer %s", this));
			}
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

		return new InetSocketAddress(address, hostAndPort.getPortOrDefault(Settings.getInstance().getDefaultListenPort()));
	}

	/** Returns true if address is loopback/link-local/site-local, false otherwise. */
	public static boolean isAddressLocal(InetAddress address) {
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
	}

}
