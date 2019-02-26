package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.network.Peer;

@XmlAccessorType(XmlAccessType.FIELD)
public class ConnectedPeer {

	public String address;
	public Long lastPing;
	public Integer lastHeight;

	public enum Direction {
		INBOUND,
		OUTBOUND;
	}

	public Direction direction;

	protected ConnectedPeer() {
	}

	public ConnectedPeer(Peer peer) {
		this.address = peer.getPeerData().getAddress().toString();
		this.lastPing = peer.getLastPing();
		this.direction = peer.isOutbound() ? Direction.OUTBOUND : Direction.INBOUND;
		this.lastHeight = peer.getPeerData() == null ? null : peer.getPeerData().getLastHeight();
	}

}
