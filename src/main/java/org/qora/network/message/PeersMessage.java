package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qora.network.Peer;

import com.google.common.primitives.Ints;

// NOTE: this legacy message only supports 4-byte IPv4 addresses and doesn't send port number either
public class PeersMessage extends Message {

	private static final int ADDRESS_LENGTH = 4;

	private List<InetAddress> peerAddresses;

	public PeersMessage(List<Peer> peers) {
		super(-1, MessageType.PEERS);

		// We have to forcibly resolve into IP addresses as we can't send hostnames
		this.peerAddresses = new ArrayList<>();

		for (Peer peer : peers) {
			try {
				InetAddress resolvedAddress = InetAddress.getByName(peer.getRemoteSocketAddress().getHostString());

				// Filter out unsupported address types
				if (resolvedAddress.getAddress().length != ADDRESS_LENGTH)
					continue;

				this.peerAddresses.add(resolvedAddress);
			} catch (UnknownHostException e) {
				// Couldn't resolve
				continue;
			}
		}
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
