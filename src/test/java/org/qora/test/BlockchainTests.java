package org.qora.test;

import org.junit.jupiter.api.Test;
import org.qora.block.BlockChain;
import org.qora.repository.DataException;

public class BlockchainTests extends Common {

	@Test
	public void testValidateOrRebuild() throws DataException {
		BlockChain.validate();
	}

}
