package repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import data.PaymentData;
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
	private HSQLDBCreatePollTransactionRepository createPollTransactionRepository;
	private HSQLDBVoteOnPollTransactionRepository voteOnPollTransactionRepository;
	private HSQLDBIssueAssetTransactionRepository issueAssetTransactionRepository;
	private HSQLDBTransferAssetTransactionRepository transferAssetTransactionRepository;
	private HSQLDBCreateOrderTransactionRepository createOrderTransactionRepository;
	private HSQLDBCancelOrderTransactionRepository cancelOrderTransactionRepository;
	private HSQLDBMultiPaymentTransactionRepository multiPaymentTransactionRepository;
	private HSQLDBMessageTransactionRepository messageTransactionRepository;

	public HSQLDBTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
		this.genesisTransactionRepository = new HSQLDBGenesisTransactionRepository(repository);
		this.paymentTransactionRepository = new HSQLDBPaymentTransactionRepository(repository);
		this.createPollTransactionRepository = new HSQLDBCreatePollTransactionRepository(repository);
		this.voteOnPollTransactionRepository = new HSQLDBVoteOnPollTransactionRepository(repository);
		this.issueAssetTransactionRepository = new HSQLDBIssueAssetTransactionRepository(repository);
		this.transferAssetTransactionRepository = new HSQLDBTransferAssetTransactionRepository(repository);
		this.createOrderTransactionRepository = new HSQLDBCreateOrderTransactionRepository(repository);
		this.cancelOrderTransactionRepository = new HSQLDBCancelOrderTransactionRepository(repository);
		this.multiPaymentTransactionRepository = new HSQLDBMultiPaymentTransactionRepository(repository);
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
			byte[] reference = rs.getBytes(2);
			byte[] creatorPublicKey = rs.getBytes(3);
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creatorPublicKey, timestamp, fee);
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
			byte[] signature = rs.getBytes(2);
			byte[] creatorPublicKey = rs.getBytes(3);
			long timestamp = rs.getTimestamp(4).getTime();
			BigDecimal fee = rs.getBigDecimal(5).setScale(8);

			return this.fromBase(type, signature, reference, creatorPublicKey, timestamp, fee);
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

			case CREATE_POLL:
				return this.createPollTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case VOTE_ON_POLL:
				return this.voteOnPollTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case ISSUE_ASSET:
				return this.issueAssetTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case TRANSFER_ASSET:
				return this.transferAssetTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case CREATE_ASSET_ORDER:
				return this.createOrderTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case CANCEL_ASSET_ORDER:
				return this.cancelOrderTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case MULTIPAYMENT:
				return this.multiPaymentTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			case MESSAGE:
				return this.messageTransactionRepository.fromBase(signature, reference, creatorPublicKey, timestamp, fee);

			default:
				return null;
		}
	}

	protected List<PaymentData> getPaymentsFromSignature(byte[] signature) throws DataException {
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT recipient, amount, asset_id FROM SharedTransactionPayments WHERE signature = ?", signature);
			if (rs == null)
				return null;

			List<PaymentData> payments = new ArrayList<PaymentData>();

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String recipient = rs.getString(1);
				BigDecimal amount = rs.getBigDecimal(2);
				long assetId = rs.getLong(3);

				payments.add(new PaymentData(recipient, assetId, amount));
			} while (rs.next());

			return payments;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch payments from repository", e);
		}
	}

	protected void savePayments(byte[] signature, List<PaymentData> payments) throws DataException {
		for (PaymentData paymentData : payments) {
			HSQLDBSaver saver = new HSQLDBSaver("SharedTransactionPayments");

			saver.bind("signature", signature).bind("recipient", paymentData.getRecipient()).bind("amount", paymentData.getAmount()).bind("asset_id",
					paymentData.getAssetId());

			try {
				saver.execute(this.repository);
			} catch (SQLException e) {
				throw new DataException("Unable to save payment into repository", e);
			}
		}
	}

	@Override
	public int getHeightFromSignature(byte[] signature) throws DataException {
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
			throw new DataException("Unable to fetch transaction's height from repository", e);
		}
	}

	@Override
	public BlockData getBlockDataFromSignature(byte[] signature) throws DataException {
		if (signature == null)
			return null;

		// Fetch block signature (if any)
		try {
			ResultSet rs = this.repository.checkedExecute("SELECT block_signature from BlockTransactions WHERE transaction_signature = ? LIMIT 1", signature);
			if (rs == null)
				return null;

			byte[] blockSignature = rs.getBytes(1);

			return this.repository.getBlockRepository().fromSignature(blockSignature);
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch transaction's block from repository", e);
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
			throw new DataException("Unable to save transaction into repository", e);
		}

		// Now call transaction-type-specific save() method
		switch (transactionData.getType()) {
			case GENESIS:
				this.genesisTransactionRepository.save(transactionData);
				break;

			case PAYMENT:
				this.paymentTransactionRepository.save(transactionData);
				break;

			case CREATE_POLL:
				this.createPollTransactionRepository.save(transactionData);
				break;

			case VOTE_ON_POLL:
				this.voteOnPollTransactionRepository.save(transactionData);
				break;

			case ISSUE_ASSET:
				this.issueAssetTransactionRepository.save(transactionData);
				break;

			case TRANSFER_ASSET:
				this.transferAssetTransactionRepository.save(transactionData);
				break;

			case CREATE_ASSET_ORDER:
				this.createOrderTransactionRepository.save(transactionData);
				break;

			case CANCEL_ASSET_ORDER:
				this.cancelOrderTransactionRepository.save(transactionData);
				break;

			case MULTIPAYMENT:
				this.multiPaymentTransactionRepository.save(transactionData);
				break;

			case MESSAGE:
				this.messageTransactionRepository.save(transactionData);
				break;

			default:
				throw new DataException("Unsupported transaction type during save into repository");
		}
	}

	@Override
	public void delete(TransactionData transactionData) throws DataException {
		// NOTE: The corresponding row in sub-table is deleted automatically by the database thanks to "ON DELETE CASCADE" in the sub-table's FOREIGN KEY
		// definition.
		try {
			this.repository.checkedExecute("DELETE FROM Transactions WHERE signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete transaction from repository", e);
		}
	}

}
