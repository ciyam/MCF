package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qora.settings.Settings;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

// NOTE: this message supports hostnames, IPv6, port numbers and IPv4 addresses (in IPv6 form)
public class PeersV2Message extends Message {

	private static final byte[] IPV6_V4_PREFIX = new byte[] {
		0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff
	};

	private List<InetSocketAddress> peerSocketAddresses;

	public PeersV2Message(List<InetSocketAddress> peerSocketAddresses) {
		this(-1, peerSocketAddresses);
	}

	private PeersV2Message(int id, List<InetSocketAddress> peerSocketAddresses) {
		super(id, MessageType.PEERS_V2);

		this.peerSocketAddresses = peerSocketAddresses;
	}

	public List<InetSocketAddress> getPeerAddresses() {
		return this.peerSocketAddresses;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		// Read entry count
		int count = byteBuffer.getInt();

		List<InetSocketAddress> peerSocketAddresses = new ArrayList<>();

		byte[] ipAddressBytes = new byte[16];
		int port;

		for (int i = 0; i < count; ++i) {
			byte addressSize = byteBuffer.get();

			if (addressSize == 0) {
				// Address size of 0 indicates IP address (always in IPv6 form)
				byteBuffer.get(ipAddressBytes);

				port = byteBuffer.getInt();

				try {
					InetAddress address = InetAddress.getByAddress(ipAddressBytes);

					peerSocketAddresses.add(new InetSocketAddress(address, port));
				} catch (UnknownHostException e) {
					// Ignore and continue
				}
			} else {
				byte[] hostnameBytes = new byte[addressSize & 0xff];
				byteBuffer.get(hostnameBytes);
				String hostname = new String(hostnameBytes, "UTF-8");

				port = byteBuffer.getInt();

				peerSocketAddresses.add(InetSocketAddress.createUnresolved(hostname, port));
			}
		}

		return new PeersV2Message(id, peerSocketAddresses);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			// First entry represents sending node but contains only port number with empty address.
			List<InetSocketAddress> socketAddresses = new ArrayList<>(this.peerSocketAddresses);
			socketAddresses.add(0, new InetSocketAddress(Settings.getInstance().getListenPort()));

			// Number of entries we are sending.
			int count = socketAddresses.size();

			for (InetSocketAddress socketAddress : socketAddresses) {
				// Hostname preferred, failing that IP address
				if (socketAddress.isUnresolved()) {
					String hostname = socketAddress.getHostString();

					byte[] hostnameBytes = hostname.getBytes("UTF-8");

					// We don't support hostnames that are longer than 256 bytes
					if (hostnameBytes.length > 256) {
						--count;
						continue;
					}

					bytes.write(hostnameBytes.length);

					bytes.write(hostnameBytes);
				} else {
					// IP address
					byte[] ipAddressBytes = socketAddress.getAddress().getAddress();

					// IPv4? Convert to IPv6 form
					if (ipAddressBytes.length == 4)
						ipAddressBytes = Bytes.concat(IPV6_V4_PREFIX, ipAddressBytes);

					// Write zero length to indicate IP address follows
					bytes.write(0);

					bytes.write(ipAddressBytes);
				}

				// Port
				bytes.write(Ints.toByteArray(socketAddress.getPort()));
			}

			// Prepend updated entry count
			byte[] countBytes = Ints.toByteArray(count);
			return Bytes.concat(countBytes, bytes.toByteArray());
		} catch (IOException e) {
			return null;
		}
	}

}
