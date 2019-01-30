package org.qora.data.network;

import java.net.InetSocketAddress;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class PeerData {

	// Properties
	private InetSocketAddress socketAddress;
	private Long lastAttempted;
	private Long lastConnected;
	private Integer lastHeight;

	// Constructors

	// necessary for JAX-RS serialization
	protected PeerData() {
	}

	public PeerData(InetSocketAddress socketAddress, Long lastAttempted, Long lastConnected, Integer lastHeight) {
		this.socketAddress = socketAddress;
		this.lastAttempted = lastAttempted;
		this.lastConnected = lastConnected;
		this.lastHeight = lastHeight;
	}

	public PeerData(InetSocketAddress socketAddress) {
		this(socketAddress, null, null, null);
	}

	// Getters / setters

	public InetSocketAddress getSocketAddress() {
		return this.socketAddress;
	}

	public Long getLastAttempted() {
		return this.lastAttempted;
	}

	public void setLastAttempted(Long lastAttempted) {
		this.lastAttempted = lastAttempted;
	}

	public Long getLastConnected() {
		return this.lastConnected;
	}

	public void setLastConnected(Long lastConnected) {
		this.lastConnected = lastConnected;
	}

	public Integer getLastHeight() {
		return this.lastHeight;
	}

	public void setLastHeight(Integer lastHeight) {
		this.lastHeight = lastHeight;
	}

}
