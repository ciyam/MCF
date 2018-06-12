package repository;

import data.block.BlockTransactionData;

public interface BlockTransactionRepository {

	public void save(BlockTransactionData blockTransactionData) throws DataException;

}
