package org.qora.network;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageType;
import org.qora.network.message.PeerIdMessage;
import org.qora.network.message.ProofMessage;
import org.qora.network.message.VersionMessage;

public enum Handshake {
	STARTED(null) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			return SELF_CHECK;
		}

		@Override
		public void action(Peer peer) {
		}
	},
	SELF_CHECK(MessageType.PEER_ID) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			PeerIdMessage peerIdMessage = (PeerIdMessage) message;
			byte[] peerId = peerIdMessage.getPeerId();

			if (Arrays.equals(peerId, Network.getInstance().getOurPeerId())) {
				// Connected to self!
				// If outgoing connection then record destination as self so we don't try again
				if (peer.isOutbound())
					Network.getInstance().noteToSelf(peer);
				else
					// We still need to send our ID so our outbound connection can mark their address as 'self'
					sendMyId(peer);

				// Handshake failure - caller will deal with disconnect
				return null;
			}

			// Set peer's ID
			peer.setPeerId(peerId);

			// Is this ID already connected? We don't want both inbound and outbound so discard inbound if possible
			Peer similarPeer = Network.getInstance().getOutboundPeerWithId(peerId);
			if (similarPeer != null && similarPeer != peer) {
				LOGGER.trace(String.format("Discarding inbound peer %s with existing ID", peer));
				return null;
			}

			return VERSION;
		}

		@Override
		public void action(Peer peer) {
			sendMyId(peer);
		}
	},
	VERSION(MessageType.VERSION) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			peer.setVersionMessage((VersionMessage) message);

			// If we're both version 2 peers then next stage is proof
			if (peer.getVersion() >= 2)
				return PROOF;

			// Fall-back for older clients (for now)
			return COMPLETED;
		}

		@Override
		public void action(Peer peer) {
			sendVersion(peer);
		}
	},
	PROOF(MessageType.PROOF) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			ProofMessage proofMessage = (ProofMessage) message;

			// Check peer's timestamp is within acceptable bounds
			if (Math.abs(proofMessage.getTimestamp() - peer.getConnectionTimestamp()) > MAX_TIMESTAMP_DELTA)
				return null;

			// If we connected outbound to peer, then this is a faked confirmation response, so we're good
			if (peer.isOutbound())
				return COMPLETED;

			// Check salt hasn't been seen before - this stops multiple peers reusing same nonce in a Sybil-like attack
			if (Proof.seenSalt(proofMessage.getSalt()))
				return null;

			if (!Proof.check(proofMessage.getTimestamp(), proofMessage.getSalt(), proofMessage.getNonce()))
				return null;

			// Proof valid
			return COMPLETED;
		}

		@Override
		public void action(Peer peer) {
			sendProof(peer);
		}
	},
	COMPLETED(null) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			// Handshake completed
			return null;
		}

		@Override
		public void action(Peer peer) {
			// Note: this is only called when we've made outbound connection
		}
	};

	private static final Logger LOGGER = LogManager.getLogger(Handshake.class);

	private static final long MAX_TIMESTAMP_DELTA = 2000; // ms

	public final MessageType expectedMessageType;

	private Handshake(MessageType expectedMessageType) {
		this.expectedMessageType = expectedMessageType;
	}

	public abstract Handshake onMessage(Peer peer, Message message);

	public abstract void action(Peer peer);

	private static void sendVersion(Peer peer) {
		long buildTimestamp = Controller.getInstance().getBuildTimestamp();
		String versionString = Controller.getInstance().getVersionString();

		Message versionMessage = new VersionMessage(buildTimestamp, versionString);
		if (!peer.sendMessage(versionMessage))
			peer.disconnect();
	}

	private static void sendMyId(Peer peer) {
		Message peerIdMessage = new PeerIdMessage(Network.getInstance().getOurPeerId());
		if (!peer.sendMessage(peerIdMessage))
			peer.disconnect();
	}

	private static void sendProof(Peer peer) {
		if (peer.isOutbound()) {
			// For outbound connections we need to generate real proof
			new Proof(peer).start();
		} else {
			// For incoming connections we only need to send a fake proof message as confirmation
			Message proofMessage = new ProofMessage(peer.getConnectionTimestamp(), 0, 0);
			if (!peer.sendMessage(proofMessage))
				peer.disconnect();
		}
	}

}
