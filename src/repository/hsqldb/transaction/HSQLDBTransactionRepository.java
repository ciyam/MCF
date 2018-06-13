package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import data.block.BlockData;
import data.transaction.TransactionData;
import qora.transaction.Transaction.TransactionType;
import repository.DataException;
import repository.TransactionRepository;
import repository.hsqldb.HSQLDBRepository;
import repository.hsqldb.HSQLDBSaver;

public class HSQLDBTransactionRepository implements TransactionRepository {

	protected HSQLDBRepository repository;
	private HSQLDBGenesisTransactionRepository genesisTransactionRepository;
	private HSQLDBPaymentTransactionRepository paymentTransactionRepository;
	private HSQLDBIssueAssetTransactionRepository issueAssetTransactionRepository;
	private HSQLDBCreateOrderTransactionRepository createOrderTransactionRepository;
	private HSQLDBMessageTransactionRepository messageTransactionRepository;

	public HSQLDBTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
		this.genesisTransactionRepository = new HSQLDBGenesisTransactionRepository(repository);
		this.paymentTransactionRepository = new HSQLDBPaymentTransactionRepository(repository);
		this.issueAssetTransactionRepository = new HSQLDBIssueAssetTransactionRepository(repository);
		this.createOrderTransactionRepository = new HSQLDBCreateOrderTransactionRepository(repository);
		this.messageTransactionRepository = new HSQLDBMessageTransactionRepository(repository);
	}

	protected HSQLDBTransactionRepository() {
	}

	public TransactionData fromSignature(byte[] signature) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT type, reference, creator, creation, fee FROM Transactions WHERE signature = ?", signature);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] reference = this.repository.getResultSetBytes(rs.getBinaryStream(2));
			byte[] creator = this.repository.getResultSetBytes(rs.getBinaryStream(3));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	public TransactionData fromReference(byte[] reference) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT type, signature, creator, creation, fee FROM Transactions WHERE reference = ?", reference);
			if (rs == null)
				return null;

			TransactionType type = TransactionType.valueOf(rs.getInt(1));
			byte[] signature = this.repository.getResultSetBytes(rs.getBinaryStream(2));
			byte[] creator = this.repository.getResultSetBytes(rs.getBinaryStream(3));
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creator, timestamp, fee);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	private TransactionData fromBase(TransactionType type, byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee)
			throws DataException {
		switch (type) {
			case GENESIS:
				return this.genesisTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case PAYMENT:
				return this.paymentTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case ISSUE_ASSET:
				return this.issueAssetTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case CREATE_ASSET_ORDER:
				return this.createOrderTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case MESSAGE:
				return this.messageTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			default:
				return null;
		}
	}

	@Override
	public int getHeight(TransactionData transactionData) {
		byte[] signature = transactionData.getSignature();
		if (signature == null)
			return 0;

		// in one go?
		try {
			ResultSet rs = this.repository.checkedExecute(
					"SELECT height from BlockTransactions JOIN Blocks ON Blocks.signature = BlockTransactions.block_signature WHERE transaction_signature = ? LIMIT 1",
					signature);

			if (rs == null)
				return 0;

			return rs.getInt(1);
		} catch (SQLException e) {
			return 0;
		}
	}

	@Override
	public BlockData toBlock(TransactionData transactionData) {
		byte[] signature = transactionData.getSignature();
		if (signature == null)
			return null;

		// Fetch block signature (if any)
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT block_signature from BlockTransactions WHERE transaction_signature = ? LIMIT 1", signature);
			if (rs == null)
				return null;

			byte[] blockSignature = this.repository.getResultSetBytes(rs.getBinaryStream(1));

			return this.repository.getBlockRepository().fromSignature(blockSignature);
		} catch (SQLException | DataException e) {
			return null;
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		HSQLDBSaver saver = new HSQLDBSaver("Transactions");
		saver.bind("signature", transactionData.getSignature()).bind("reference", transactionData.getReference()).bind("type", transactionData.getType().value)
				.bind("creator", transactionData.getCreatorPublicKey()).bind("creation", new Timestamp(transactionData.getTimestamp()))
				.bind("fee", transactionData.getFee()).bind("milestone_block", null);
		try {
			saver.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException(e);
		}
	}

	@Override
	public void delete(TransactionData transactionData) throws DataException {
		// NOTE: The corresponding row in sub-table is deleted automatically by the database thanks to "ON DELETE CASCADE" in the sub-table's FOREIGN KEY
		// definition.
		try {
			this.repository.checkedExecute("DELETE FROM Transactions WHERE signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			throw new DataException(e);
		}
	}

}
