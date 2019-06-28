package org.qora.repository.hsqldb.transaction;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.stream;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.PaymentData;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.TransactionRepository;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;

import static org.qora.transaction.Transaction.TransactionType.*;

public class HSQLDBTransactionRepository implements TransactionRepository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBTransactionRepository.class);

	public static class RepositorySubclassInfo {
		public Class<?> clazz;
		public Constructor<?> constructor;
		public Method fromBaseMethod;
		public Method saveMethod;
	}

	private static final RepositorySubclassInfo[] subclassInfos;
	static {
		subclassInfos = new RepositorySubclassInfo[TransactionType.values().length + 1];

		for (TransactionType txType : TransactionType.values()) {
			RepositorySubclassInfo subclassInfo = new RepositorySubclassInfo();

			try {
				subclassInfo.clazz = Class.forName(
						String.join("", HSQLDBTransactionRepository.class.getPackage().getName(), ".", "HSQLDB", txType.className, "TransactionRepository"));
			} catch (ClassNotFoundException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass not found for transaction type \"%s\"", txType.name()));
				continue;
			}

			try {
				subclassInfo.constructor = subclassInfo.clazz.getConstructor(HSQLDBRepository.class);
			} catch (NoSuchMethodException | IllegalArgumentException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass constructor not found for transaction type \"%s\"", txType.name()));
				continue;
			}

			try {
				// params: long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature
				subclassInfo.fromBaseMethod = subclassInfo.clazz.getDeclaredMethod("fromBase", long.class, int.class, byte[].class, byte[].class, BigDecimal.class, byte[].class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass's \"fromBase\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.saveMethod = subclassInfo.clazz.getDeclaredMethod("save", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass's \"save\" method not found for transaction type \"%s\"", txType.name()));
			}

			subclassInfos[txType.value] = subclassInfo;
		}

		LOGGER.trace("Static init reflection completed");
	}

	private HSQLDBTransactionRepository[] repositoryByTxType;

	protected HSQLDBRepository repository;

	public HSQLDBTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;

		this.repositoryByTxType = new HSQLDBTransactionRepository[TransactionType.values().length + 1];

		for (TransactionType txType : TransactionType.values()) {
			RepositorySubclassInfo subclassInfo = subclassInfos[txType.value];

			if (subclassInfo == null)
				continue;

			if (subclassInfo.constructor == null)
				continue;

			try {
				this.repositoryByTxType[txType.value] = (HSQLDBTransactionRepository) subclassInfo.constructor.newInstance(repository);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
				continue;
			}
		}
	}

	// Never called
	protected HSQLDBTransactionRepository() {
	}

	@Override
	public TransactionData fromSignature(byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT type, reference, creator, creation, fee, tx_group_id FROM Transactions WHERE signature = ?",
				signature)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));
			byte[] reference = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);
			int txGroupId = resultSet.getInt(6);

			TransactionData transactionData = this.fromBase(type, timestamp, txGroupId, reference, creatorPublicKey, fee, signature);
			return maybeIncludeBlockHeight(transactionData);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	@Override
	public TransactionData fromReference(byte[] reference) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT type, signature, creator, creation, fee, tx_group_id FROM Transactions WHERE reference = ?",
				reference)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));
			byte[] signature = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);
			int txGroupId = resultSet.getInt(6);

			TransactionData transactionData = this.fromBase(type, timestamp, txGroupId, reference, creatorPublicKey, fee, signature);
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

	private TransactionData fromBase(TransactionType type, long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature)
			throws DataException {
		HSQLDBTransactionRepository txRepository = repositoryByTxType[type.value];
		if (txRepository == null)
			throw new DataException("Unsupported transaction type [" + type.name() + "] during fetch from HSQLDB repository");

		try {
			// params: long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature
			return (TransactionData) subclassInfos[type.value].fromBaseMethod.invoke(txRepository, timestamp, txGroupId, reference, creatorPublicKey, fee, signature);
		} catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
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
	public boolean exists(byte[] signature) throws DataException {
		try {
			return this.repository.exists("Transactions", "signature = ?", signature);
		} catch (SQLException e) {
			throw new DataException("Unable to check for transaction in repository", e);
		}
	}

	@Override
	public List<byte[]> getSignaturesInvolvingAddress(String address) throws DataException {
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
	public List<byte[]> getSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, TransactionType txType, String address,
			ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse) throws DataException {
		List<byte[]> signatures = new ArrayList<byte[]>();

		boolean hasAddress = address != null && !address.isEmpty();
		boolean hasTxType = txType != null;
		boolean hasHeightRange = startBlock != null || blockLimit != null;

		if (hasHeightRange && startBlock == null)
			startBlock = (reverse == null || !reverse) ? 1 : this.repository.getBlockRepository().getBlockchainHeight() - blockLimit;

		String signatureColumn = "Transactions.signature";
		List<String> whereClauses = new ArrayList<String>();
		String groupBy = "";
		List<Object> bindParams = new ArrayList<Object>();

		// Tables, starting with Transactions
		String tables = "Transactions";

		// BlockTransactions if we want confirmed transactions
		switch (confirmationStatus) {
			case BOTH:
				break;

			case CONFIRMED:
				tables += " JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature";

				if (hasHeightRange)
					tables += " JOIN Blocks ON Blocks.signature = BlockTransactions.block_signature";

				break;

			case UNCONFIRMED:
				tables += " LEFT OUTER JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature";
				whereClauses.add("BlockTransactions.transaction_signature IS NULL");
				break;
		}

		if (hasAddress) {
			tables += " JOIN TransactionParticipants ON TransactionParticipants.signature = Transactions.signature";
			groupBy = " GROUP BY TransactionParticipants.signature, Transactions.creation";
			signatureColumn = "TransactionParticipants.signature";
		}

		// WHERE clauses next
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

		String sql = "SELECT " + signatureColumn + " FROM " + tables;

		if (!whereClauses.isEmpty())
			sql += " WHERE " + String.join(" AND ", whereClauses);

		if (!groupBy.isEmpty())
			sql += groupBy;

		sql += " ORDER BY Transactions.creation";
		sql += (reverse == null || !reverse) ? " ASC" : " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		LOGGER.trace(String.format("Transaction search SQL: %s", sql));

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
	public List<TransactionData> getAssetTransactions(int assetId, ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		TransactionType[] transactionTypes = new TransactionType[] {
			ISSUE_ASSET, TRANSFER_ASSET, CREATE_ASSET_ORDER, CANCEL_ASSET_ORDER
		};
		List<String> typeValueStrings = Arrays.asList(transactionTypes).stream().map(type -> String.valueOf(type.value)).collect(Collectors.toList());

		String sql = "SELECT Transactions.signature FROM Transactions";

		// BlockTransactions if we want confirmed transactions
		switch (confirmationStatus) {
			case BOTH:
				break;

			case CONFIRMED:
				sql += " JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature";
				break;

			case UNCONFIRMED:
				sql += " LEFT OUTER JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature";
				break;
		}

		for (TransactionType type : transactionTypes)
			sql += " LEFT OUTER JOIN " + type.className + "Transactions USING (signature)";

		// assetID isn't in Cancel Asset Order so we need to join to the order
		sql += " LEFT OUTER JOIN AssetOrders ON AssetOrders.asset_order_id = CancelAssetOrderTransactions.asset_order_id";

		sql += " WHERE Transactions.type IN (" + String.join(", ", typeValueStrings) + ")";

		// BlockTransactions if we want confirmed transactions
		switch (confirmationStatus) {
			case BOTH:
				break;

			case CONFIRMED:
				break;

			case UNCONFIRMED:
				sql += " AND BlockTransactions.transaction_signature IS NULL";
				break;
		}

		sql += " AND (";
		sql += "IssueAssetTransactions.asset_id = " + assetId;
		sql += " OR ";
		sql += "TransferAssetTransactions.asset_id = " + assetId;
		sql += " OR ";
		sql += "CreateAssetOrderTransactions.have_asset_id = " + assetId;
		sql += " OR ";
		sql += "CreateAssetOrderTransactions.want_asset_id = " + assetId;
		sql += " OR ";
		sql += "AssetOrders.have_asset_id = " + assetId;
		sql += " OR ";
		sql += "AssetOrders.want_asset_id = " + assetId;
		sql += ") GROUP BY Transactions.signature, Transactions.creation ORDER BY Transactions.creation";

		sql += (reverse == null || !reverse) ? " ASC" : " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch asset-related transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch asset-related transactions from repository", e);
		}
	}

	@Override
	public List<TransactionData> getPendingTransactions(Integer txGroupId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String[] txTypesNeedingApproval = stream(Transaction.TransactionType.values())
		.filter(txType -> txType.needsApproval)
		.map(txType -> String.valueOf(txType.value))
		.toArray(String[]::new);

		String txTypes = String.join(", ", txTypesNeedingApproval);

		/*
		 *  We only want transactions matching certain types needing approval,
		 *  with txGroupId not set to NO_GROUP and where auto-approval won't
		 *  happen due to the transaction creator being an admin of that group.
		 */
		String sql = "SELECT signature FROM UnconfirmedTransactions "
				+ "NATURAL JOIN Transactions "
				+ "LEFT OUTER JOIN Accounts ON Accounts.public_key = Transactions.creator "
				+ "LEFT OUTER JOIN GroupAdmins ON GroupAdmins.admin = Accounts.account AND GroupAdmins.group_id = Transactions.tx_group_id "
				+ "WHERE Transactions.tx_group_id != ? AND GroupAdmins.admin IS NULL "
				+ "AND Transactions.type IN (" + txTypes + ") "
				+ "ORDER BY creation";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += ", signature";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		// Find transactions with no corresponding row in BlockTransactions
		try (ResultSet resultSet = this.repository.checkedExecute(sql, Group.NO_GROUP)) {
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
	public int countTransactionApprovals(int txGroupId, byte[] signature) throws DataException {
		// Fetch total number of approvals for signature
		// NOT simply number of GROUP_APPROVAL transactions as some may be rejecting transaction, or changed opinions
		// Also make sure that GROUP_APPROVAL transaction's admin is still an admin of group

		// Sub-query SQL to find latest GroupApprovalTransaction relating to passed signature
		String latestApprovalSql = "SELECT admin AS creator, MAX(creation) AS creation FROM GroupApprovalTransactions NATURAL JOIN Transactions WHERE pending_signature = ? GROUP BY admin";

		String sql = "SELECT COUNT(*) FROM "
				+ "(" + latestApprovalSql + ") "
				+ "NATURAL JOIN Transactions "
				+ "NATURAL JOIN GroupApprovalTransactions "
				+ "LEFT OUTER JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature "
				+ "LEFT OUTER JOIN Accounts ON Accounts.public_key = GroupApprovalTransactions.admin "
				+ "LEFT OUTER JOIN GroupAdmins ON GroupAdmins.admin = Accounts.account "
				+ "WHERE approval = TRUE AND GroupAdmins.group_id = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, signature, txGroupId)) {
			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to count transaction group-admin approvals from repository", e);
		}
	}

	@Override
	public List<GroupApprovalTransactionData> getLatestApprovals(byte[] pendingSignature, byte[] adminPublicKey) throws DataException {
		// Fetch latest approvals for signature
		// NOT simply number of GROUP_APPROVAL transactions as some may be rejecting transaction, or changed opinions
		// Also make sure that GROUP_APPROVAL transaction's admin is still an admin of group

		Object[] bindArgs;

		// Sub-query SQL to find latest GroupApprovalTransaction relating to passed signature
		String latestApprovalSql = "SELECT admin AS creator, MAX(creation) AS creation FROM GroupApprovalTransactions NATURAL JOIN Transactions WHERE pending_signature = ? ";
		if (adminPublicKey != null)
			latestApprovalSql += "AND admin = ? ";
		latestApprovalSql += "GROUP BY admin";

		String sql = "SELECT signature FROM "
				+ "(" + latestApprovalSql + ") "
				+ "NATURAL JOIN Transactions "
				+ "NATURAL JOIN GroupApprovalTransactions "
				+ "LEFT OUTER JOIN BlockTransactions ON BlockTransactions.transaction_signature = Transactions.signature "
				+ "LEFT OUTER JOIN Accounts ON Accounts.public_key = GroupApprovalTransactions.admin "
				+ "LEFT OUTER JOIN GroupAdmins ON GroupAdmins.admin = Accounts.account "
				+ "WHERE approval = TRUE AND GroupAdmins.group_id = Transactions.tx_group_id";

		if (adminPublicKey != null)
			bindArgs = new Object[] { pendingSignature, adminPublicKey };
		else
			bindArgs = new Object[] { pendingSignature };

		List<GroupApprovalTransactionData> approvals = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindArgs)) {
			if (resultSet == null)
				return approvals;

			do {
				byte[] signature = resultSet.getBytes(1);

				approvals.add((GroupApprovalTransactionData) this.fromSignature(signature));
			} while (resultSet.next());

			return approvals;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest transaction group-admin approvals from repository", e);
		}
	}

	@Override
	public boolean isConfirmed(byte[] signature) throws DataException {
		try {
			return this.repository.exists("BlockTransactions", "transaction_signature = ?", signature);
		} catch (SQLException e) {
			throw new DataException("Unable to check whether transaction is confirmed in repository", e);
		}
	}

	@Override
	public List<TransactionData> getUnconfirmedTransactions(Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT signature FROM UnconfirmedTransactions ORDER BY creation";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += ", signature";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		// Find transactions with no corresponding row in BlockTransactions
		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
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
				.bind("fee", transactionData.getFee()).bind("milestone_block", null).bind("tx_group_id", transactionData.getTxGroupId());
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
			subclassInfos[type.value].saveMethod.invoke(txRepository, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
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
