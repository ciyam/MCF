package org.qora.test.common;

import java.math.BigDecimal;

import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.data.block.BlockData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class BlockUtils {

	public static BigDecimal getNextBlockReward(Repository repository) throws DataException {
		int currentHeight = repository.getBlockRepository().getBlockchainHeight();

		return BlockChain.getInstance().getRewardAtHeight(currentHeight + 1);
	}

	public static void orphanLastBlock(Repository repository) throws DataException {
		BlockData blockData = repository.getBlockRepository().getLastBlock();
		Block block = new Block(repository, blockData);
		block.orphan();
		repository.saveChanges();
	}

}
