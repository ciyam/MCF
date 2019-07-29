package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.data.network.PeerData;
import org.qora.network.Handshake;
import org.qora.network.Peer;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class ConnectedPeer {

	public enum Direction {
		INBOUND,
		OUTBOUND;
	}
	public Direction direction;
	public Handshake handshakeStatus;
	public Long lastPing;
	public Long connectedWhen;
	public Long peersConnectedWhen;

	public String address;
	public String version;
	public Long buildTimestamp;

	public Integer lastHeight;
	@Schema(example = "base58")
	public byte[] lastBlockSignature;
	public Long lastBlockTimestamp;

	protected ConnectedPeer() {
	}

	public ConnectedPeer(Peer peer) {
		this.direction = peer.isOutbound() ? Direction.OUTBOUND : Direction.INBOUND;
		this.handshakeStatus = peer.getHandshakeStatus();
		this.lastPing = peer.getLastPing();

		PeerData peerData = peer.getPeerData();
		this.connectedWhen = peer.getConnectionTimestamp();
		this.peersConnectedWhen = peer.getPeersConnectionTimestamp();

		this.address = peerData.getAddress().toString();
		if (peer.getVersionMessage() != null) {
			this.version = peer.getVersionMessage().getVersionString();
			this.buildTimestamp = peer.getVersionMessage().getBuildTimestamp();
		}

		this.lastHeight = peer.getLastHeight();
		this.lastBlockSignature = peer.getLastBlockSignature();
		this.lastBlockTimestamp = peer.getLastBlockTimestamp();
	}

}
