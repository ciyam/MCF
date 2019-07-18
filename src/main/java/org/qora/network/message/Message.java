package org.qora.network.message;

import java.util.Map;

import org.qora.controller.Controller;
import org.qora.crypto.Crypto;

import com.google.common.primitives.Ints;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class Message {

	// MAGIC(4) + TYPE(4) + HAS-ID(1) + ID?(4) + DATA-SIZE(4) + CHECKSUM?(4) + DATA?(*)
	private static final int MAGIC_LENGTH = 4;
	private static final int CHECKSUM_LENGTH = 4;

	private static final int MAX_DATA_SIZE = 1024 * 1024; // 1MB

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
		BLOCK_SUMMARIES(15);

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

		public Message fromBytes(int id, byte[] data) {
			if (this.fromByteBuffer == null)
				return null;

			try {
				return (Message) this.fromByteBuffer.invoke(null, id, data == null ? null : ByteBuffer.wrap(data));
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				return null;
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

	public static Message fromStream(DataInputStream in) throws SocketTimeoutException {
		try {
			// Read only enough bytes to cover Message "magic" preamble
			byte[] messageMagic = new byte[MAGIC_LENGTH];
			in.readFully(messageMagic);

			if (!Arrays.equals(messageMagic, Controller.getInstance().getMessageMagic()))
				// Didn't receive correct Message "magic"
				return null;

			int typeValue = in.readInt();
			MessageType messageType = MessageType.valueOf(typeValue);
			if (messageType == null)
				// Unrecognised message type
				return null;

			// Find supporting object

			int hasId = in.read();
			int id = -1;
			if (hasId != 0) {
				id = in.readInt();

				if (id <= 0)
					// Invalid ID
					return null;
			}

			int dataSize = in.readInt();

			if (dataSize > MAX_DATA_SIZE)
				// Too large
				return null;

			byte[] data = null;
			if (dataSize > 0) {
				byte[] expectedChecksum = new byte[CHECKSUM_LENGTH];
				in.readFully(expectedChecksum);

				data = new byte[dataSize];
				in.readFully(data);

				// Test checksum
				byte[] actualChecksum = generateChecksum(data);
				if (!Arrays.equals(expectedChecksum, actualChecksum))
					return null;
			}

			return messageType.fromBytes(id, data);
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (IOException e) {
			return null;
		}
	}

	protected static byte[] generateChecksum(byte[] data) {
		return Arrays.copyOfRange(Crypto.digest(data), 0, CHECKSUM_LENGTH);
	}

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			// Magic
			bytes.write(Controller.getInstance().getMessageMagic());

			bytes.write(Ints.toByteArray(this.type.value));

			if (this.hasId()) {
				bytes.write(1);

				bytes.write(Ints.toByteArray(this.id));
			} else {
				bytes.write(0);
			}

			byte[] data = this.toData();
			if (data == null)
				return null;

			bytes.write(Ints.toByteArray(data.length));

			if (data.length > 0) {
				bytes.write(generateChecksum(data));
				bytes.write(data);
			}

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	protected abstract byte[] toData();

}
