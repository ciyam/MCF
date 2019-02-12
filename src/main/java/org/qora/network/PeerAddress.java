package org.qora.network;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.qora.settings.Settings;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

/**
 * Convenience class for encapsulating/parsing/rendering/converting peer addresses
 * including late-stage resolving before actual use by a socket.
 */
public class PeerAddress {

	// Properties
	private String host;
	private int port;

	private PeerAddress(String host, int port) {
		this.host = host;
		this.port = port;
	}

	// Constructors

	// For JAXB
	protected PeerAddress() {
	}

	/** Constructs new PeerAddress using remote address from passed connected socket. */
	public static PeerAddress fromSocket(Socket socket) {
		InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
		InetAddress address = socketAddress.getAddress();

		String host = InetAddresses.toAddrString(address);

		// Make sure we encapsulate IPv6 addresses in brackets
		if (address instanceof Inet6Address)
			host = "[" + host + "]";

		return new PeerAddress(host, socketAddress.getPort());
	}

	/**
	 * Constructs new PeerAddress using hostname or literal IP address and optional port.<br>
	 * Literal IPv6 addresses must be enclosed within square brackets.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>peer.example.com
	 * <li>peer.example.com:9084
	 * <li>192.0.2.1
	 * <li>192.0.2.1:9084
	 * <li>[2001:db8::1]
	 * <li>[2001:db8::1]:9084
	 * </ul>
	 * <p>
	 * Not allowed:
	 * <ul>
	 * <li>2001:db8::1
	 * <li>2001:db8::1:9084
	 * </ul>
	 */
	public static PeerAddress fromString(String addressString) throws IllegalArgumentException {
		boolean isBracketed = addressString.startsWith("[");

		// Attempt to parse string into host and port
		HostAndPort hostAndPort = HostAndPort.fromString(addressString).withDefaultPort(Settings.DEFAULT_LISTEN_PORT).requireBracketsForIPv6();

		String host = hostAndPort.getHost();
		if (host.isEmpty())
			throw new IllegalArgumentException("Empty host part");

		// Validate IP literals by attempting to convert to InetAddress, without DNS lookups
		if (host.contains(":") || host.matches("[0-9.]+"))
			InetAddresses.forString(host);

		// If we've reached this far then we have a valid address

		// Make sure we encapsulate IPv6 addresses in brackets
		if (isBracketed)
			host = "[" + host + "]";

		return new PeerAddress(host, hostAndPort.getPort());
	}

	// Getters

	/** Returns hostname or literal IP address, bracketed if IPv6 */
	public String getHost() {
		return this.host;
	}

	public int getPort() {
		return this.port;
	}

	// Conversions

	/** Returns InetSocketAddress for use with Socket.connect(), or throws UnknownHostException if address could not be resolved by DNS lookup. */
	public InetSocketAddress toSocketAddress() throws UnknownHostException {
		// Attempt to construct new InetSocketAddress with DNS lookups.
		// There's no control here over whether IPv6 or IPv4 will be used.
		InetSocketAddress socketAddress = new InetSocketAddress(this.host, this.port);

		// If we couldn't resolve then return null
		if (socketAddress.isUnresolved())
			throw new UnknownHostException();

		return socketAddress;
	}

	@Override
	public String toString() {
		return this.host + ":" + this.port;
	}

	// Utilities

	/** Returns true if other PeerAddress has same port and same case-insensitive host part, without DNS lookups */
	public boolean equals(PeerAddress other) {
		// Ports must match
		if (this.port != other.port)
			return false;

		// Compare host parts but without DNS lookups
		return this.host.equalsIgnoreCase(other.host);
	}

}
