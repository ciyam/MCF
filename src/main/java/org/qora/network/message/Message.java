package org.qora.network.message;

import java.util.Map;

import org.qora.crypto.Crypto;
import org.qora.network.Network;

import com.google.common.primitives.Ints;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class Message {

	// MAGIC(4) + TYPE(4) + HAS-ID(1) + ID?(4) + DATA-SIZE(4) + CHECKSUM?(4) + DATA?(*)
	private static final int MAGIC_LENGTH = 4;
	private static final int CHECKSUM_LENGTH = 4;

	private static final int MAX_DATA_SIZE = 1024 * 1024; // 1MB

	@SuppressWarnings("serial")
	public static class MessageException extends Exception {
		public MessageException() {
		}

		public MessageException(String message) {
			super(message);
		}

		public MessageException(String message, Throwable cause) {
			super(message, cause);
		}

		public MessageException(Throwable cause) {
			super(cause);
		}
	}

	public enum MessageType {
		GET_PEERS(1),
		PEERS(2),
		HEIGHT(3),
		GET_SIGNATURES(4),
		SIGNATURES(5),
		GET_BLOCK(6),
		BLOCK(7),
		TRANSACTION(8),
		PING(9),
		VERSION(10),
		PEER_ID(11),
		PROOF(12),
		PEERS_V2(13),
		GET_BLOCK_SUMMARIES(14),
		BLOCK_SUMMARIES(15),
		GET_SIGNATURES_V2(16),
		PEER_VERIFY(17),
		VERIFICATION_CODES(18),
		HEIGHT_V2(19),
		GET_TRANSACTION(20),
		GET_UNCONFIRMED_TRANSACTIONS(21),
		TRANSACTION_SIGNATURES(22),
		GET_ARBITRARY_DATA(23),
		ARBITRARY_DATA(24);

		public final int value;
		public final Method fromByteBuffer;

		private static final Map<Integer, MessageType> map = stream(MessageType.values())
				.collect(toMap(messageType -> messageType.value, messageType -> messageType));

		private MessageType(int value) {
			this.value = value;

			String[] classNameParts = this.name().toLowerCase().split("_");

			for (int i = 0; i < classNameParts.length; ++i)
				classNameParts[i] = classNameParts[i].substring(0, 1).toUpperCase().concat(classNameParts[i].substring(1));

			String className = String.join("", classNameParts);

			Method fromByteBuffer;
			try {
				Class<?> subclass = Class.forName(String.join("", Message.class.getPackage().getName(), ".", className, "Message"));

				fromByteBuffer = subclass.getDeclaredMethod("fromByteBuffer", int.class, ByteBuffer.class);
			} catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
				fromByteBuffer = null;
			}

			this.fromByteBuffer = fromByteBuffer;
		}

		public static MessageType valueOf(int value) {
			return map.get(value);
		}

		public Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
			if (this.fromByteBuffer == null)
				throw new MessageException("Unsupported message type [" + value + "] during conversion from bytes");

			try {
				return (Message) this.fromByteBuffer.invoke(null, id, byteBuffer);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				if (e.getCause() instanceof BufferUnderflowException)
					throw new MessageException("Byte data too short for " + name() + " message");

				throw new MessageException("Internal error with " + name() + " message during conversion from bytes");
			}
		}
	}

	private int id;
	private MessageType type;

	protected Message(int id, MessageType type) {
		this.id = id;
		this.type = type;
	}

	protected Message(MessageType type) {
		this(-1, type);
	}

	public boolean hasId() {
		return this.id != -1;
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public MessageType getType() {
		return this.type;
	}

	/**
	 * Attempt to read a message from byte buffer.
	 * 
	 * @param byteBuffer
	 * @return null if no complete message can be read
	 * @throws MessageException
	 */
	public static Message fromByteBuffer(ByteBuffer byteBuffer) throws MessageException {
		try {
			byteBuffer.flip();

			ByteBuffer readBuffer = byteBuffer.asReadOnlyBuffer();

			// Read only enough bytes to cover Message "magic" preamble
			byte[] messageMagic = new byte[MAGIC_LENGTH];
			readBuffer.get(messageMagic);

			if (!Arrays.equals(messageMagic, Network.getInstance().getMessageMagic()))
				// Didn't receive correct Message "magic"
				throw new MessageException("Received incorrect message 'magic'");

			// Find supporting object
			int typeValue = readBuffer.getInt();
			MessageType messageType = MessageType.valueOf(typeValue);
			if (messageType == null)
				// Unrecognised message type
				throw new MessageException(String.format("Received unknown message type [%d]", typeValue));

			// Optional message ID
			byte hasId = readBuffer.get();
			int id = -1;
			if (hasId != 0) {
				id = readBuffer.getInt();

				if (id <= 0)
					// Invalid ID
					throw new MessageException("Invalid negative ID");
			}

			int dataSize = readBuffer.getInt();

			if (dataSize > MAX_DATA_SIZE)
				// Too large
				throw new MessageException(String.format("Declared data length %d larger than max allowed %d", dataSize, MAX_DATA_SIZE));

			ByteBuffer dataSlice = null;
			if (dataSize > 0) {
				byte[] expectedChecksum = new byte[CHECKSUM_LENGTH];
				readBuffer.get(expectedChecksum);

				// Remember this position in readBuffer so we can pass to Message subclass
				dataSlice = readBuffer.slice();

				// Consume data from buffer
				byte[] data = new byte[dataSize];
				readBuffer.get(data);

				// We successfully read all the data bytes, so we can set limit on dataSlice
				dataSlice.limit(dataSize);

				// Test checksum
				byte[] actualChecksum = generateChecksum(data);
				if (!Arrays.equals(expectedChecksum, actualChecksum))
					throw new MessageException("Message checksum incorrect");
			}

			Message message = messageType.fromByteBuffer(id, dataSlice);

			// We successfully read a message, so bump byteBuffer's position to reflect this
			byteBuffer.position(readBuffer.position());

			return message;
		} catch (BufferUnderflowException e) {
			// Not enough bytes to fully decode message...
			return null;
		} finally {
			byteBuffer.compact();
		}
	}

	protected static byte[] generateChecksum(byte[] data) {
		return Arrays.copyOfRange(Crypto.digest(data), 0, CHECKSUM_LENGTH);
	}

	public byte[] toBytes() throws MessageException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);

			// Magic
			bytes.write(Network.getInstance().getMessageMagic());

			bytes.write(Ints.toByteArray(this.type.value));

			if (this.hasId()) {
				bytes.write(1);

				bytes.write(Ints.toByteArray(this.id));
			} else {
				bytes.write(0);
			}

			byte[] data = this.toData();
			if (data == null)
				throw new MessageException("Missing data payload");

			bytes.write(Ints.toByteArray(data.length));

			if (data.length > 0) {
				bytes.write(generateChecksum(data));
				bytes.write(data);
			}

			if (bytes.size() > MAX_DATA_SIZE)
				throw new MessageException(String.format("About to send message with length %d larger than allowed %d", bytes.size(), MAX_DATA_SIZE));

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new MessageException("Failed to serialize message", e);
		}
	}

	protected abstract byte[] toData();

}
