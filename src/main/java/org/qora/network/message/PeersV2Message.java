package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qora.network.PeerAddress;
import org.qora.settings.Settings;

import com.google.common.primitives.Ints;

// NOTE: this message supports hostnames, literal IP addresses (IPv4 and IPv6) with port numbers
public class PeersV2Message extends Message {

	private List<PeerAddress> peerAddresses;

	public PeersV2Message(List<PeerAddress> peerAddresses) {
		this(-1, peerAddresses);
	}

	private PeersV2Message(int id, List<PeerAddress> peerAddresses) {
		super(id, MessageType.PEERS_V2);

		this.peerAddresses = peerAddresses;
	}

	public List<PeerAddress> getPeerAddresses() {
		return this.peerAddresses;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		// Read entry count
		int count = byteBuffer.getInt();

		List<PeerAddress> peerAddresses = new ArrayList<>();

		for (int i = 0; i < count; ++i) {
			byte addressSize = byteBuffer.get();

			byte[] addressBytes = new byte[addressSize & 0xff];
			byteBuffer.get(addressBytes);
			String addressString = new String(addressBytes, "UTF-8");

			try {
				PeerAddress peerAddress = PeerAddress.fromString(addressString);
				peerAddresses.add(peerAddress);
			} catch (IllegalArgumentException e) {
				// Not valid - ignore
			}
		}

		return new PeersV2Message(id, peerAddresses);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			List<byte[]> addresses = new ArrayList<>();

			// First entry represents sending node but contains only port number with empty address.
			addresses.add(new String("0.0.0.0:" + Settings.getInstance().getListenPort()).getBytes("UTF-8"));

			for (PeerAddress peerAddress : this.peerAddresses)
				addresses.add(peerAddress.toString().getBytes("UTF-8"));

			// We can't send addresses that are longer than 255 bytes as length itself is encoded in one byte.
			addresses.removeIf(addressString -> addressString.length > 255);

			// Serialize

			// Number of entries
			bytes.write(Ints.toByteArray(addresses.size()));

			for (byte[] address : addresses) {
				bytes.write(address.length);
				bytes.write(address);
			}

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
