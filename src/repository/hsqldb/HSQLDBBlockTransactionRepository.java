package repository.hsqldb;

import java.sql.SQLException;

import data.block.BlockTransactionData;
import repository.BlockTransactionRepository;
import repository.DataException;

public class HSQLDBBlockTransactionRepository implements BlockTransactionRepository {

	protected HSQLDBRepository repository;

	public HSQLDBBlockTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	public void save(BlockTransactionData blockTransactionData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("BlockTransactions");
		saveHelper.bind("block_signature", blockTransactionData.getBlockSignature()).bind("sequence", blockTransactionData.getSequence())
				.bind("transaction_signature", blockTransactionData.getTransactionSignature());

		try {
			saveHelper.execute(this.repository.connection);
		} catch (SQLException e) {
			throw new DataException("Unable to save BlockTransaction into repository", e);
		}
	}

}
