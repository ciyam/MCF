package org.qora.network;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.network.message.Message;
import org.qora.network.message.Message.MessageType;
import org.qora.network.message.PeerIdMessage;
import org.qora.network.message.PeerVerifyMessage;
import org.qora.network.message.ProofMessage;
import org.qora.network.message.VerificationCodesMessage;
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

			// Is this ID already connected inbound or outbound?
			Peer otherInboundPeer = Network.getInstance().getInboundPeerWithId(peerId);

			// Extra checks on inbound peers with known IDs, to prevent ID stealing
			if (!peer.isOutbound() && otherInboundPeer != null) {
				Peer otherOutboundPeer = Network.getInstance().getOutboundHandshakedPeerWithId(peerId);

				if (otherOutboundPeer == null) {
					// We already have an inbound peer with this ID, but no outgoing peer with which to request verification
					LOGGER.trace(String.format("Discarding inbound peer %s with existing ID", peer));
					return null;
				} else {
					// Use corresponding outbound peer to verify inbound
					LOGGER.trace(String.format("We will be using outbound peer %s to verify inbound peer %s with same ID", otherOutboundPeer, peer));

					// Discard peer's ID
					// peer.setPeerId(peerId);

					// Generate verification codes for later
					peer.generateVerificationCodes();
				}
			} else {
				// Set peer's ID
				peer.setPeerId(peerId);
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
			if (Math.abs(proofMessage.getTimestamp() - peer.getConnectionTimestamp()) > MAX_TIMESTAMP_DELTA) {
				LOGGER.debug(String.format("Rejecting PROOF from %s as timestamp delta %d greater than max %d", peer, Math.abs(proofMessage.getTimestamp() - peer.getConnectionTimestamp()), MAX_TIMESTAMP_DELTA));
				return null;
			}

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
	},
	PEER_VERIFY(null) {
		@Override
		public Handshake onMessage(Peer peer, Message message) {
			// We only accept PEER_VERIFY messages
			if (message.getType() != Message.MessageType.PEER_VERIFY)
				return PEER_VERIFY;

			// Check returned code against expected
			PeerVerifyMessage peerVerifyMessage = (PeerVerifyMessage) message;

			if (!Arrays.equals(peerVerifyMessage.getVerificationCode(), peer.getVerificationCodeExpected()))
				return null;

			// Drop other inbound peers with the same ID
			for (Peer otherPeer : Network.getInstance().getConnectedPeers())
				if (!otherPeer.isOutbound() && otherPeer.getPeerId() != null && Arrays.equals(otherPeer.getPeerId(), peer.getPendingPeerId()))
					otherPeer.disconnect("doppelganger");

			// Tidy up
			peer.setVerificationCodes(null, null);
			peer.setPeerId(peer.getPendingPeerId());
			peer.setPendingPeerId(null);

			// Completed for real this time
			return COMPLETED;
		}

		@Override
		public void action(Peer peer) {
			// Send VERIFICATION_CODE to other peer (that we connected to)
			// Send PEER_VERIFY to peer
			sendVerificationCodes(peer);
		}
	};

	private static final Logger LOGGER = LogManager.getLogger(Handshake.class);

	/** Maximum allowed difference between peer's reported timestamp and when they connected, in milliseconds. */
	private static final long MAX_TIMESTAMP_DELTA = 5000; // ms

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
			peer.disconnect("failed to send version");
	}

	private static void sendMyId(Peer peer) {
		Message peerIdMessage = new PeerIdMessage(Network.getInstance().getOurPeerId());
		if (!peer.sendMessage(peerIdMessage))
			peer.disconnect("failed to send peer ID");
	}

	private static void sendProof(Peer peer) {
		if (peer.isOutbound()) {
			// For outbound connections we need to generate real proof
			new Proof(peer).start();
		} else {
			// For incoming connections we only need to send a fake proof message as confirmation
			Message proofMessage = new ProofMessage(peer.getConnectionTimestamp(), 0, 0);
			if (!peer.sendMessage(proofMessage))
				peer.disconnect("failed to send proof");
		}
	}

	private static void sendVerificationCodes(Peer peer) {
		Peer otherOutboundPeer = Network.getInstance().getOutboundHandshakedPeerWithId(peer.getPendingPeerId());

		// Send VERIFICATION_CODES to peer
		Message verificationCodesMessage = new VerificationCodesMessage(peer.getVerificationCodeSent(), peer.getVerificationCodeExpected());
		if (!otherOutboundPeer.sendMessage(verificationCodesMessage)) {
			peer.disconnect("failed to send verification codes"); // give up with this peer instead
			return;
		}

		// Send PEER_VERIFY to peer
		Message peerVerifyMessage = new PeerVerifyMessage(peer.getVerificationCodeSent());
		if (!peer.sendMessage(peerVerifyMessage))
			peer.disconnect("failed to send verification code");
	}

}
