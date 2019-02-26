package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.google.common.primitives.Ints;

// NOTE: this legacy message only supports 4-byte IPv4 addresses and doesn't send port number either
public class PeersMessage extends Message {

	private static final int ADDRESS_LENGTH = 4;

	private List<InetAddress> peerAddresses;

	public PeersMessage(List<InetAddress> peerAddresses) {
		super(MessageType.PEERS);

		this.peerAddresses = new ArrayList<>(peerAddresses);

		// Legacy PEERS message doesn't support IPv6
		this.peerAddresses.removeIf(address -> address instanceof Inet6Address);
	}

	private PeersMessage(int id, List<InetAddress> peerAddresses) {
		super(id, MessageType.PEERS);

		this.peerAddresses = peerAddresses;
	}

	public List<InetAddress> getPeerAddresses() {
		return this.peerAddresses;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		int count = bytes.getInt();

		if (bytes.remaining() != count * ADDRESS_LENGTH)
			return null;

		List<InetAddress> peerAddresses = new ArrayList<>();

		byte[] addressBytes = new byte[ADDRESS_LENGTH];

		try {
			for (int i = 0; i < count; ++i) {
				bytes.get(addressBytes);
				peerAddresses.add(InetAddress.getByAddress(addressBytes));
			}
		} catch (UnknownHostException e) {
			return null;
		}

		return new PeersMessage(id, peerAddresses);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(this.peerAddresses.size()));

			for (InetAddress peerAddress : this.peerAddresses)
				bytes.write(peerAddress.getAddress());

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
