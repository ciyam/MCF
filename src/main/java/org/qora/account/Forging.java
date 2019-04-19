package org.qora.account;

import org.qora.block.BlockChain;
import org.qora.repository.DataException;

/** Relating to whether accounts can forge. */
public class Forging {

	/** Returns mask for account flags for forging bits. */
	public static int getForgingMask() {
		return (1 << BlockChain.getInstance().getForgingTiers().size()) - 1;
	}

	public static boolean canForge(Account account) throws DataException {
		Integer flags = account.getFlags();
		return flags != null && (flags & getForgingMask()) != 0;
	}

}
