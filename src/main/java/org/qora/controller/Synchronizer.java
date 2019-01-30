package org.qora.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.network.Peer;

public class Synchronizer {

	private static final Logger LOGGER = LogManager.getLogger(Synchronizer.class);

	private static Synchronizer instance;

	private Synchronizer() {
	}

	public static Synchronizer getInstance() {
		if (instance == null)
			instance = new Synchronizer();

		return instance;
	}

	public void synchronize(Peer peer) {
		// If we're already synchronizing with another peer then return

		LOGGER.info(String.format("Synchronizing with peer %s", peer.getRemoteSocketAddress()));

		// Peer has different latest block sig to us

		// find common block?

		// if common block is too far behind us then we're on massively different forks so give up, maybe human invention required to download desired fork

		// unwind to common block (unless common block is our latest block)

		// apply some newer blocks from peer

		// commit

		// If our block gen creates a block while we do this - what happens?
		// does repository serialization prevent issues?

		// blockgen: block 123: pay X from A to B, commit
		// sync: block 122 orphaned, replacement blocks 122 through 129 applied, commit

		// and vice versa?

		// sync: block 122 orphaned, replacement blocks 122 through 129 applied, commit
		// blockgen: block 123: pay X from A to B, commit

		// simply block syncing when generating and vice versa by grabbing a Controller-owned non-blocking mutex?
	}

}
