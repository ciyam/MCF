package org.qora.repository.hsqldb.transaction;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.data.PaymentData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.TransactionRepository;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.TransactionType;

public class HSQLDBTransactionRepository implements TransactionRepository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBTransactionRepository.class);

	private HSQLDBTransactionRepository[] repositoryByTxType;
	protected HSQLDBRepository repository;

	public HSQLDBTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;

		this.repositoryByTxType = new HSQLDBTransactionRepository[256];

		for (TransactionType txType : TransactionType.values()) {
			Class<?> repositoryClass = getClassByTxType(txType);
			if (repositoryClass == null)
				continue;

			try {
				Constructor<?> constructor = repositoryClass.getConstructor(HSQLDBRepository.class);
				HSQLDBTransactionRepository txRepository = (HSQLDBTransactionRepository) constructor.newInstance(repository);
				this.repositoryByTxType[txType.value] = txRepository;
			} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
				continue;
			}
		}
	}

	protected HSQLDBTransactionRepository() {
	}

	private static Class<?> getClassByTxType(TransactionType txType) {
		try {
			return Class.forName(String.join("", HSQLDBTransactionRepository.class.getPackage().getName(), ".", "HSQLDB", txType.className, "TransactionRepository"));
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	@Override
	public TransactionData fromSignature(byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT type, reference, creator, creation, fee FROM Transactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));
			byte[] reference = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);

			TransactionData transactionData = this.fromBase(type, signature, reference, creatorPublicKey, timestamp, fee);
			return maybeIncludeBlockHeight(transactionData);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	@Override
	public TransactionData fromReference(byte[] reference) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT type, signature, creator, creation, fee FROM Transactions WHERE reference = ?",
				reference)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));
			byte[] signature = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);

			TransactionData transactionData = this.fromBase(type, signature, reference, creatorPublicKey, timestamp, fee);
			return maybeIncludeBlockHeight(transactionData);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	private TransactionData maybeIncludeBlockHeight(TransactionData transactionData) throws DataException {
		int blockHeight = getHeightFromSignature(transactionData.getSignature());
		if (blockHeight != 0)
			transactionData.setBlockHeight(blockHeight);

		return transactionData;
	}

	@Override
	public TransactionData fromHeightAndSequence(int height, int sequence) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT transaction_signature FROM BlockTransactions JOIN Blocks ON signature = block_signature WHERE height = ? AND sequence = ?", height,
				sequence)) {
			if (resultSet == null)
				return null;

			byte[] signature = resultSet.getBytes(1);

			return this.fromSignature(signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	private TransactionData fromBase(TransactionType type, byte[] signature, byte[] reference, byte[] creatorPublicKey, long timestamp, BigDecimal fee)
			throws DataException {
		HSQLDBTransactionRepository txRepository = repositoryByTxType[type.value];
		if (txRepository == null)
			throw new DataException("Unsupported transaction type [" + type.name() + "] during fetch from HSQLDB repository");

		try {
			Method method = txRepository.getClass().getDeclaredMethod("fromBase", byte[].class, byte[].class, byte[].class, long.class, BigDecimal.class);
			return (TransactionData) method.invoke(txRepository, signature, reference, creatorPublicKey, timestamp, fee);
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new DataException("Unsupported transaction type [" + type.name() + "] during fetch from HSQLDB repository");
		}
	}

	/**
	 * Returns payments associated with a transaction's signature.
	 * <p>
	 * Used by various transaction types, like Payment, MultiPayment, ArbitraryTransaction.
	 * 
	 * @param signature
	 * @return list of payments, empty if none found
	 * @throws DataException
	 */
	protected List<PaymentData> getPaymentsFromSignature(byte[] signature) throws DataException {
		List<PaymentData> payments = new ArrayList<PaymentData>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT recipient, amount, asset_id FROM SharedTransactionPayments WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return payments;

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String recipient = resultSet.getString(1);
				BigDecimal amount = resultSet.getBigDecimal(2);
				long assetId = resultSet.getLong(3);

				payments.add(new PaymentData(recipient, assetId, amount));
			} while (resultSet.next());

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

		// Fetch height using join via block's transactions
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT height from BlockTransactions JOIN Blocks ON Blocks.signature = BlockTransactions.block_signature WHERE transaction_signature = ? LIMIT 1",
				signature)) {

			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction's height from repository", e);
		}
	}

	@Override
	public List<byte[]> getAllSignaturesInvolvingAddress(String address) throws DataException {
		List<byte[]> signatures = new ArrayList<byte[]>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT signature FROM TransactionRecipients WHERE participant = ?", address)) {
			if (resultSet == null)
				return signatures;

			do {
				byte[] signature = resultSet.getBytes(1);

				signatures.add(signature);
			} while (resultSet.next());

			return signatures;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch involved transaction signatures from repository", e);
		}
	}

	@Override
	public void saveParticipants(TransactionData transactionData, List<String> participants) throws DataException {
		byte[] signature = transactionData.getSignature();

		try {
			for (String participant : participants) {
				HSQLDBSaver saver = new HSQLDBSaver("TransactionParticipants");

				saver.bind("signature", signature).bind("participant", participant);

				saver.execute(this.repository);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to save transaction participant into repository", e);
		}
	}

	@Override
	public void deleteParticipants(TransactionData transactionData) throws DataException {
		try {
			this.repository.delete("TransactionParticipants", "signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete transaction participants from repository", e);
		}
	}

	@Override
	public List<byte[]> getAllSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, TransactionType txType, String address) throws DataException {
		List<byte[]> signatures = new ArrayList<byte[]>();

		boolean hasAddress = address != null && !address.isEmpty();
		boolean hasTxType = txType != null;
		boolean hasHeightRange = startBlock != null || blockLimit != null;

		if (hasHeightRange && startBlock == null)
			startBlock = 1;

		String signatureColumn = "NULL";
		List<Object> bindParams = new ArrayList<Object>();
		String groupBy = "";

		// Table JOINs first
		List<String> tableJoins = new ArrayList<String>();

		// Always JOIN BlockTransactions as we only ever want confirmed transactions
		tableJoins.add("Blocks");
		tableJoins.add("BlockTransactions ON BlockTransactions.block_signature = Blocks.signature");
		signatureColumn = "BlockTransactions.transaction_signature";

		// Always JOIN Transactions as we want to order by timestamp
		tableJoins.add("Transactions ON Transactions.signature = BlockTransactions.transaction_signature");
		signatureColumn = "Transactions.signature";

		if (hasAddress) {
			tableJoins.add("TransactionParticipants ON TransactionParticipants.signature = Transactions.signature");
			signatureColumn = "TransactionParticipants.signature";
			groupBy = " GROUP BY TransactionParticipants.signature, Transactions.creation";
		}

		// WHERE clauses next
		List<String> whereClauses = new ArrayList<String>();

		if (hasHeightRange) {
			whereClauses.add("Blocks.height >= " + startBlock);

			if (blockLimit != null)
				whereClauses.add("Blocks.height < " + (startBlock + blockLimit));
		}

		if (hasTxType)
			whereClauses.add("Transactions.type = " + txType.value);

		if (hasAddress) {
			whereClauses.add("TransactionParticipants.participant = ?");
			bindParams.add(address);
		}

		String sql = "SELECT " + signatureColumn + " FROM " + String.join(" JOIN ", tableJoins) + " WHERE " + String.join(" AND ", whereClauses) + groupBy + " ORDER BY Transactions.creation ASC";
		LOGGER.trace(sql);

		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams.toArray())) {
			if (resultSet == null)
				return signatures;

			do {
				byte[] signature = resultSet.getBytes(1);

				signatures.add(signature);
			} while (resultSet.next());

			return signatures;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching transaction signatures from repository", e);
		}
	}

	@Override
	public List<TransactionData> getAllUnconfirmedTransactions() throws DataException {
		List<TransactionData> transactions = new ArrayList<TransactionData>();

		// Find transactions with no corresponding row in BlockTransactions
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT signature FROM UnconfirmedTransactions ORDER BY creation ASC, signature ASC")) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch unconfirmed transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch unconfirmed transactions from repository", e);
		}
	}

	@Override
	public void confirmTransaction(byte[] signature) throws DataException {
		try {
			this.repository.delete("UnconfirmedTransactions", "signature = ?", signature);
		} catch (SQLException e) {
			throw new DataException("Unable to remove transaction from unconfirmed transactions repository", e);
		}
	}

	@Override
	public void unconfirmTransaction(TransactionData transactionData) throws DataException {
		HSQLDBSaver saver = new HSQLDBSaver("UnconfirmedTransactions");
		saver.bind("signature", transactionData.getSignature()).bind("creation", new Timestamp(transactionData.getTimestamp()));
		try {
			saver.execute(repository);
		} catch (SQLException e) {
			throw new DataException("Unable to add transaction to unconfirmed transactions repository", e);
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
		TransactionType type = transactionData.getType();
		HSQLDBTransactionRepository txRepository = repositoryByTxType[type.value];
		if (txRepository == null)
			throw new DataException("Unsupported transaction type [" + type.name() + "] during save into HSQLDB repository");

		try {
			Method method = txRepository.getClass().getDeclaredMethod("save", TransactionData.class);
			method.invoke(txRepository, transactionData);
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new DataException("Unsupported transaction type [" + type.name() + "] during save into HSQLDB repository");
		}
	}

	@Override
	public void delete(TransactionData transactionData) throws DataException {
		// NOTE: The corresponding row in sub-table is deleted automatically by the database thanks to "ON DELETE CASCADE" in the sub-table's FOREIGN KEY
		// definition.
		try {
			this.repository.delete("Transactions", "signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to delete transaction from repository", e);
		}
		try {
			this.repository.delete("UnconfirmedTransactions", "signature = ?", transactionData.getSignature());
		} catch (SQLException e) {
			throw new DataException("Unable to remove transaction from unconfirmed transactions repository", e);
		}
	}

}
