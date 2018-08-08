import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Ints;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.block.Block;
import qora.block.Block.ValidationResult;
import qora.block.BlockChain;
import qora.crypto.Crypto;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import transform.TransformationException;
import transform.block.BlockTransformer;
import utils.Pair;

public class v1feeder extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(v1feeder.class);

	private static final int INACTIVITY_TIMEOUT = 60 * 1000; // milliseconds
	private static final int CONNECTION_TIMEOUT = 2 * 1000; // milliseconds
	private static final int PING_INTERVAL = 10 * 1000; // milliseconds
	private static final int DEFAULT_PORT = 9084;

	private static final int MAGIC_LENGTH = 4;
	// private static final int TYPE_LENGTH = 4;
	private static final int HAS_ID_LENGTH = 1;
	// private static final int ID_LENGTH = 4;
	// private static final int DATA_SIZE_LENGTH = 4;
	private static final int CHECKSUM_LENGTH = 4;

	private static final int SIGNATURE_LENGTH = 128;

	private static final byte[] MAINNET_MAGIC = { 0x12, 0x34, 0x56, 0x78 };

	// private static final int GET_PEERS_TYPE = 1;
	// private static final int PEERS_TYPE = 2;
	private static final int HEIGHT_TYPE = 3;
	private static final int GET_SIGNATURES_TYPE = 4;
	private static final int SIGNATURES_TYPE = 5;
	private static final int GET_BLOCK_TYPE = 6;
	private static final int BLOCK_TYPE = 7;
	// private static final int TRANSACTION_TYPE = 8;
	private static final int PING_TYPE = 9;
	private static final int VERSION_TYPE = 10;
	// private static final int FIND_MYSELF_TYPE = 11;

	private Socket socket;
	private OutputStream out;

	private static final int IDLE_STATE = 0;
	private static final int AWAITING_HEADERS_STATE = 1;
	private static final int HAVE_HEADERS_STATE = 2;
	private static final int AWAITING_BLOCK_STATE = 3;
	private static final int HAVE_BLOCK_STATE = 4;
	private int feederState = IDLE_STATE;
	private int messageId = -1;

	private long lastPingTimestamp = System.currentTimeMillis();
	private List<byte[]> signatures = new ArrayList<byte[]>();

	private v1feeder(String address, int port) throws InterruptedException {
		try {
			for (int i = 0; i < 10; ++i)
				try {
					// Create new socket for connection to peer
					this.socket = new Socket();

					// Collate this.address and destination port
					InetSocketAddress socketAddress = new InetSocketAddress(address, port);

					// Attempt to connect, with timeout from settings
					this.socket.connect(socketAddress, CONNECTION_TIMEOUT);
					break;
				} catch (SocketTimeoutException e) {
					LOGGER.info("Timed out trying to connect to " + address + " - retrying");
					Thread.sleep(1000);
					this.socket = null;
				} catch (Exception e) {
					LOGGER.error("Failed to connect to " + address, e);
					return;
				}

			// No connection after retries?
			if (this.socket == null)
				return;

			// Enable TCP keep-alive packets
			this.socket.setKeepAlive(true);

			// Inactivity timeout
			this.socket.setSoTimeout(INACTIVITY_TIMEOUT);

			// Grab reference to output stream
			this.out = socket.getOutputStream();

			// Start main communication thread
			this.start();
		} catch (SocketException e) {
			LOGGER.error("Failed to set socket timeout for address " + address, e);
		} catch (IOException e) {
			LOGGER.error("Failed to get output stream for address " + address, e);
		}
	}

	private byte[] createMessage(int type, boolean hasId, Integer id, byte[] data) throws IOException {
		if (data == null)
			data = new byte[0];

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		bytes.write(MAINNET_MAGIC);

		bytes.write(Ints.toByteArray(type));

		byte[] hasIdBytes = new byte[] { (byte) (hasId ? 1 : 0) };
		bytes.write(hasIdBytes);

		if (hasId) {
			if (id == null)
				id = (int) ((Math.random() * 1000000) + 1);

			bytes.write(Ints.toByteArray(id));
		}

		bytes.write(Ints.toByteArray(data.length));

		if (data.length > 0) {
			byte[] checksum = Crypto.digest(data);
			bytes.write(checksum, 0, CHECKSUM_LENGTH);

			bytes.write(data);
		}

		LOGGER.trace("Creating message type [" + type + "] with " + (hasId ? "id [" + id + "]" : "no id") + " and data length " + data.length);

		return bytes.toByteArray();
	}

	private void sendMessage(byte[] message) throws IOException {
		synchronized (this.out) {
			this.out.write(message);
			this.out.flush();
		}
	}

	private void processMessage(int type, int id, byte[] data) throws IOException {
		LOGGER.trace("Received message type [" + type + "] with id [" + id + "] and data length " + data.length);

		ByteBuffer byteBuffer = ByteBuffer.wrap(data);
		switch (type) {
			case HEIGHT_TYPE:
				int height = byteBuffer.getInt();

				LOGGER.info("Peer height: " + height);
				break;

			case SIGNATURES_TYPE:
				// shove into list
				int numSignatures = byteBuffer.getInt();

				while (numSignatures-- > 0) {
					byte[] signature = new byte[SIGNATURE_LENGTH];
					byteBuffer.get(signature);
					signatures.add(signature);
				}

				LOGGER.trace("We now have " + signatures.size() + " signature(s) to process");

				feederState = HAVE_HEADERS_STATE;
				break;

			case BLOCK_TYPE:
				// If messageId doesn't match then discard
				if (id != this.messageId)
					break;

				// read block and process
				int claimedHeight = byteBuffer.getInt();

				LOGGER.info("Received block allegedly at height " + claimedHeight);

				byte[] blockBytes = new byte[byteBuffer.remaining()];
				byteBuffer.get(blockBytes);

				Pair<BlockData, List<TransactionData>> blockInfo = null;

				try {
					blockInfo = BlockTransformer.fromBytes(blockBytes);
				} catch (TransformationException e) {
					LOGGER.error("Couldn't parse block bytes from peer", e);
					throw new RuntimeException("Couldn't parse block bytes from peer", e);
				}

				try (final Repository repository = RepositoryManager.getRepository()) {
					Block block = new Block(repository, blockInfo.getA(), blockInfo.getB());

					if (!block.isSignatureValid()) {
						LOGGER.error("Invalid block signature");
						throw new RuntimeException("Invalid block signature");
					}

					ValidationResult result = block.isValid();

					if (result != ValidationResult.OK) {
						LOGGER.error("Invalid block, validation result code: " + result.value);
						throw new RuntimeException("Invalid block, validation result code: " + result.value);
					}

					block.process();
					repository.saveChanges();
				} catch (DataException e) {
					LOGGER.error("Unable to process block", e);
					throw new RuntimeException("Unable to process block", e);
				}

				feederState = HAVE_BLOCK_STATE;
				break;

			case PING_TYPE:
				LOGGER.trace("Sending pong for ping [" + id + "]");
				byte[] pongMessage = createMessage(PING_TYPE, true, id, null);
				sendMessage(pongMessage);
				break;

			case VERSION_TYPE:
				@SuppressWarnings("unused")
				long timestamp = byteBuffer.getLong();
				int versionLength = byteBuffer.getInt();
				byte[] versionBytes = new byte[versionLength];
				byteBuffer.get(versionBytes);
				String version = new String(versionBytes, Charset.forName("UTF-8"));

				LOGGER.info("Peer version info: " + version);
				break;

			default:
				LOGGER.trace("Discarding message type [" + type + "] with id [" + id + "] and data length " + data.length);
		}
	}

	private int parseBuffer(byte[] buffer, int bufferEnd) throws IOException {
		int newBufferEnd = bufferEnd;

		try {
			ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bufferEnd);

			// Check magic
			byte[] magic = new byte[MAGIC_LENGTH];
			byteBuffer.get(magic);
			if (!Arrays.equals(magic, MAINNET_MAGIC)) {
				// bad data - discard whole buffer
				return 0;
			}

			int type = byteBuffer.getInt();

			byte[] hasId = new byte[HAS_ID_LENGTH];
			byteBuffer.get(hasId);

			int id = -1;
			if (hasId[0] == (byte) 1)
				id = byteBuffer.getInt();

			int dataSize = byteBuffer.getInt();
			byte[] data = new byte[dataSize];
			if (dataSize > 0) {
				byte[] checksum = new byte[CHECKSUM_LENGTH];
				byteBuffer.get(checksum);

				byteBuffer.get(data);
			}

			// We have a full message - remove from buffer
			int nextMessageOffset = byteBuffer.position();
			newBufferEnd = bufferEnd - nextMessageOffset;
			byteBuffer = null;

			System.arraycopy(buffer, nextMessageOffset, buffer, 0, newBufferEnd);

			// Process message
			processMessage(type, id, data);
		} catch (BufferUnderflowException e) {
			// Not enough data
		}

		return newBufferEnd;
	}

	@Override
	public void run() {
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			byte[] buffer = new byte[2 * 1024 * 1024]; // 2MB
			int bufferEnd = 0;

			// Send our height
			try (final Repository repository = RepositoryManager.getRepository()) {
				int height = repository.getBlockRepository().getBlockchainHeight();
				LOGGER.trace("Sending our height " + height + " to peer");
				byte[] heightMessage = createMessage(HEIGHT_TYPE, false, null, Ints.toByteArray(height));
				sendMessage(heightMessage);
			}

			while (true) {
				// Anything to read?
				if (in.available() > 0) {
					// read message
					int numRead = in.read(buffer, bufferEnd, in.available());
					if (numRead == -1) {
						// input EOF
						LOGGER.info("Socket EOF");
						return;
					}

					bufferEnd += numRead;
				}

				if (bufferEnd > 0) {
					// attempt to parse
					bufferEnd = parseBuffer(buffer, bufferEnd);
				}

				// Do we need to send a ping message?
				if (System.currentTimeMillis() - lastPingTimestamp >= PING_INTERVAL) {
					byte[] pingMessage = createMessage(PING_TYPE, true, null, null);
					sendMessage(pingMessage);
					lastPingTimestamp = System.currentTimeMillis();
				}

				byte[] signature = null;
				switch (feederState) {
					case IDLE_STATE:
						// Get signature from our highest block
						try (final Repository repository = RepositoryManager.getRepository()) {
							BlockData blockData = repository.getBlockRepository().getLastBlock();

							if (blockData != null)
								signature = blockData.getSignature();
						}

						// done?
						if (signature == null) {
							LOGGER.warn("No last block in repository?");
							return;
						}

						LOGGER.trace("Requesting more signatures...");
						byte[] getSignaturesMessage = createMessage(GET_SIGNATURES_TYPE, true, null, signature);
						sendMessage(getSignaturesMessage);
						feederState = AWAITING_HEADERS_STATE;
						break;

					case HAVE_HEADERS_STATE:
					case HAVE_BLOCK_STATE:
						// request next block?
						if (signatures.size() == 0) {
							feederState = IDLE_STATE;
							break;
						}

						LOGGER.trace("Requesting next block...");
						signature = signatures.remove(0);
						this.messageId = (int) ((Math.random() * 1000000) + 1);
						byte[] getBlockMessage = createMessage(GET_BLOCK_TYPE, true, this.messageId, signature);
						sendMessage(getBlockMessage);
						feederState = AWAITING_BLOCK_STATE;
						break;
				}
			}
		} catch (IOException | DataException | RuntimeException e) {
			// give up
			LOGGER.info("Exiting", e);
		}

		try {
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: v1feeder v1-node-address [port]");
			System.exit(1);
		}

		try {
			test.Common.setRepository();
		} catch (DataException e) {
			LOGGER.error("Couldn't connect to repository", e);
			System.exit(2);
		}

		try {
			BlockChain.validate();
		} catch (DataException e) {
			LOGGER.error("Couldn't validate repository", e);
			System.exit(2);
		}

		// connect to v1 node
		String address = args[0];
		int port = args.length > 1 ? Integer.valueOf(args[1]) : DEFAULT_PORT;

		try {
			new v1feeder(address, port).join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		LOGGER.info("Exiting v1feeder");

		try {
			test.Common.closeRepository();
		} catch (DataException e) {
			e.printStackTrace();
		}
	}

}
