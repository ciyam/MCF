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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.PaymentData;
import org.qora.data.group.GroupApprovalData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.TransactionRepository;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import static org.qora.transaction.Transaction.TransactionType.*;

public class HSQLDBTransactionRepository implements TransactionRepository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBTransactionRepository.class);

	public static class RepositorySubclassInfo {
		public Class<?> clazz;
		public Constructor<?> constructor;
		public Method fromBaseMethod;
		public Method saveMethod;
		public Method deleteMethod;
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
				subclassInfo.fromBaseMethod = subclassInfo.clazz.getDeclaredMethod("fromBase", BaseTransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass's \"fromBase\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.saveMethod = subclassInfo.clazz.getDeclaredMethod("save", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("HSQLDBTransactionRepository subclass's \"save\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.deleteMethod = subclassInfo.clazz.getDeclaredMethod("delete", TransactionData.class);
			} catch (NoSuchMethodException e) {
				// Subclass has no "delete" method - this is OK
				subclassInfo.deleteMethod = null;
			} catch (IllegalArgumentException | SecurityException e) {
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

	// Fetching transactions / transaction height

	@Override
	public TransactionData fromSignature(byte[] signature) throws DataException {
		final String sql = "SELECT type, reference, creator, creation, fee, tx_group_id, block_height, approval_status, approval_height FROM Transactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, signature)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));

			byte[] reference = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);
			int txGroupId = resultSet.getInt(6);

			Integer blockHeight = resultSet.getInt(7);
			if (blockHeight == 0 && resultSet.wasNull())
				blockHeight = null;

			ApprovalStatus approvalStatus = ApprovalStatus.valueOf(resultSet.getInt(8));
			Integer approvalHeight = resultSet.getInt(9);
			if (approvalHeight == 0 && resultSet.wasNull())
				approvalHeight = null;

			BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, blockHeight, approvalHeight, signature);
			TransactionData transactionData = this.fromBase(type, baseTransactionData);

			return transactionData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	@Override
	public TransactionData fromReference(byte[] reference) throws DataException {
		final String sql = "SELECT type, signature, creator, creation, fee, tx_group_id, block_height, approval_status, approval_height FROM Transactions WHERE reference = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, reference)) {
			if (resultSet == null)
				return null;

			TransactionType type = TransactionType.valueOf(resultSet.getInt(1));

			byte[] signature = resultSet.getBytes(2);
			byte[] creatorPublicKey = resultSet.getBytes(3);
			long timestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			BigDecimal fee = resultSet.getBigDecimal(5).setScale(8);
			int txGroupId = resultSet.getInt(6);

			Integer blockHeight = resultSet.getInt(7);
			if (blockHeight == 0 && resultSet.wasNull())
				blockHeight = null;

			ApprovalStatus approvalStatus = ApprovalStatus.valueOf(resultSet.getInt(8));
			Integer approvalHeight = resultSet.getInt(9);
			if (approvalHeight == 0 && resultSet.wasNull())
				approvalHeight = null;

			BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, blockHeight, approvalHeight, signature);
			TransactionData transactionData = this.fromBase(type, baseTransactionData);

			return transactionData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction from repository", e);
		}
	}

	@Override
	public TransactionData fromHeightAndSequence(int height, int sequence) throws DataException {
		final String sql = "SELECT transaction_signature FROM BlockTransactions JOIN Blocks ON signature = block_signature WHERE height = ? AND sequence = ?";
		
		try (ResultSet resultSet = this.repository.checkedExecute(sql, height, sequence)) {
			if (resultSet == null)
				return null;

			byte[] signature = resultSet.getBytes(1);

			return this.fromSignature(signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction height and sequence from repository", e);
		}
	}

	private TransactionData fromBase(TransactionType type, BaseTransactionData baseTransactionData) throws DataException {
		HSQLDBTransactionRepository txRepository = repositoryByTxType[type.value];

		if (txRepository == null)
			throw new DataException("Unsupported transaction type [" + type.name() + "] during fetch from HSQLDB repository");

		try {
			return (TransactionData) subclassInfos[type.value].fromBaseMethod.invoke(txRepository, baseTransactionData);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof DataException)
				throw (DataException) e.getCause();

			throw new DataException("Unsupported transaction type [" + type.name() + "] during fetch from HSQLDB repository");
		} catch (IllegalArgumentException | IllegalAccessException e) {
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
		final String sql = "SELECT recipient, amount, asset_id FROM SharedTransactionPayments WHERE signature = ?";

		List<PaymentData> payments = new ArrayList<PaymentData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, signature)) {
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

		final String sql = "SELECT block_height from Transactions WHERE signature = ? LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, signature)) {
			if (resultSet == null)
				return 0;

			Integer blockHeight = resultSet.getInt(1);
			if (blockHeight == 0 && resultSet.wasNull())
				return 0;

			return blockHeight;
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

	// Transaction participants

	@Override
	public List<byte[]> getSignaturesInvolvingAddress(String address) throws DataException {
		final String sql = "SELECT signature FROM TransactionRecipients WHERE participant = ?";

		List<byte[]> signatures = new ArrayList<byte[]>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, address)) {
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

	// Searching transactions

	@Override
	public Map<TransactionType, Integer> getTransactionSummary(int startHeight, int endHeight) throws DataException {
		String sql = "SELECT type, COUNT(signature) FROM Transactions "
			+ "WHERE block_height BETWEEN ? AND ? "
			+ "GROUP BY type";

		Map<TransactionType, Integer> transactionCounts = new HashMap<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, startHeight, endHeight)) {
			if (resultSet == null)
				return transactionCounts;

			do {
				int type = resultSet.getInt(1);
				int count = resultSet.getInt(2);

				transactionCounts.put(TransactionType.valueOf(type), count);
			} while (resultSet.next());

			return transactionCounts;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transaction counts from repository", e);
		}
	}

	@Override
	public List<byte[]> getSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, Integer txGroupId,
			List<TransactionType> txTypes, Integer service, String address,
			ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse) throws DataException {
		List<byte[]> signatures = new ArrayList<byte[]>();

		boolean hasAddress = address != null && !address.isEmpty();
		boolean hasTxTypes = txTypes != null && !txTypes.isEmpty();
		boolean hasHeightRange = startBlock != null || blockLimit != null;

		if (hasHeightRange && startBlock == null)
			startBlock = (reverse == null || !reverse) ? 1 : this.repository.getBlockRepository().getBlockchainHeight() - blockLimit;

		String signatureColumn = "Transactions.signature";
		List<String> whereClauses = new ArrayList<String>();
		String groupBy = "";
		List<Object> bindParams = new ArrayList<Object>();

		// Tables, starting with Transactions
		String tables = "Transactions";

		if (hasAddress) {
			tables += " JOIN TransactionParticipants ON TransactionParticipants.signature = Transactions.signature";
			groupBy = " GROUP BY TransactionParticipants.signature, Transactions.creation";
			signatureColumn = "TransactionParticipants.signature";
		}

		if (service != null) {
			// This is for ARBITRARY transactions
			tables += " LEFT OUTER JOIN ArbitraryTransactions ON ArbitraryTransactions.signature = Transactions.signature";
		}

		// WHERE clauses next

		// Confirmation status
		switch (confirmationStatus) {
			case BOTH:
				break;

			case CONFIRMED:
				whereClauses.add("Transactions.block_height IS NOT NULL");
				break;

			case UNCONFIRMED:
				whereClauses.add("Transactions.block_height IS NULL");
				break;
		}

		// Height range
		if (hasHeightRange) {
			whereClauses.add("Transactions.block_height >= " + startBlock);

			if (blockLimit != null)
				whereClauses.add("Transactions.block_height < " + (startBlock + blockLimit));
		}

		if (txGroupId != null) {
			whereClauses.add("Transactions.tx_group_id = ?");
			bindParams.add(txGroupId);
		}

		if (hasTxTypes) {
			whereClauses.add("Transactions.type IN (" + HSQLDBRepository.nPlaceholders(txTypes.size()) + ")");
			bindParams.addAll(txTypes.stream().map(txType -> txType.value).collect(Collectors.toList()));
		}

		if (service != null) {
			whereClauses.add("ArbitraryTransactions.service = ?");
			bindParams.add(service);
		}

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
	public List<TransactionData> getAssetTransactions(long assetId, ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		TransactionType[] transactionTypes = new TransactionType[] {
			ISSUE_ASSET, TRANSFER_ASSET, CREATE_ASSET_ORDER, CANCEL_ASSET_ORDER
		};

		List<String> typeValueStrings = Arrays.asList(transactionTypes).stream().map(type -> String.valueOf(type.value)).collect(Collectors.toList());

		String sql = "SELECT Transactions.signature FROM Transactions";

		for (TransactionType type : transactionTypes)
			sql += " LEFT OUTER JOIN " + type.className + "Transactions USING (signature)";

		// assetID isn't in Cancel Asset Order so we need to join to the order
		sql += " LEFT OUTER JOIN AssetOrders ON AssetOrders.asset_order_id = CancelAssetOrderTransactions.asset_order_id";

		sql += " WHERE Transactions.type IN (" + String.join(", ", typeValueStrings) + ")";

		// Confirmation status
		switch (confirmationStatus) {
			case BOTH:
				break;

			case CONFIRMED:
				sql += " AND Transactions.block_height IS NOT NULL";
				break;

			case UNCONFIRMED:
				sql += " AND Transactions.block_height IS NULL";
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
	public List<TransferAssetTransactionData> getAssetTransfers(long assetId, String address, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		List<Object> bindParams = new ArrayList<>(3);

		String sql = "SELECT creation, tx_group_id, reference, fee, signature, sender, block_height, approval_status, approval_height, recipient, amount, asset_name "
				+ "FROM TransferAssetTransactions JOIN Transactions USING (signature) ";

		if (address != null)
			sql += "JOIN Accounts ON public_key = sender ";

		sql += "JOIN Assets USING (asset_id) WHERE asset_id = ? ";
		bindParams.add(assetId);

		if (address != null) {
			sql += "AND ? IN (account, recipient) ";
			bindParams.add(address);
		}

		sql += "ORDER by creation ";

		sql += (reverse == null || !reverse) ? "ASC" : "DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransferAssetTransactionData> assetTransfers = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams.toArray())) {
			if (resultSet == null)
				return assetTransfers;

			do {
				long timestamp = resultSet.getTimestamp(1, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				int txGroupId = resultSet.getInt(2);
				byte[] reference = resultSet.getBytes(3);
				BigDecimal fee = resultSet.getBigDecimal(4).setScale(8);
				byte[] signature = resultSet.getBytes(5);
				byte[] creatorPublicKey = resultSet.getBytes(6);

				Integer blockHeight = resultSet.getInt(7);
				if (blockHeight == 0 && resultSet.wasNull())
					blockHeight = null;

				ApprovalStatus approvalStatus = ApprovalStatus.valueOf(resultSet.getInt(8));

				Integer approvalHeight = resultSet.getInt(9);
				if (approvalHeight == 0 && resultSet.wasNull())
					approvalHeight = null;

				BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, blockHeight, approvalHeight, signature);

				String recipient = resultSet.getString(10);
				BigDecimal amount = resultSet.getBigDecimal(11);
				String assetName = resultSet.getString(12);

				assetTransfers.add(new TransferAssetTransactionData(baseTransactionData, recipient, amount, assetId, assetName));
			} while (resultSet.next());

			return assetTransfers;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset-transfer transactions from repository", e);
		}
	}

	@Override
	public List<TransactionData> getApprovalPendingTransactions(Integer txGroupId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT signature FROM Transactions WHERE Transactions.approval_status = ? ";

		List<Object> bindParams = new ArrayList<>();
		bindParams.add(ApprovalStatus.PENDING.value);

		if (txGroupId != null) {
			sql += "AND Transactions.tx_group_id = ? ";
			bindParams.add(txGroupId);
		}

		sql += "ORDER BY creation";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += ", signature";
		if (reverse != null && reverse)
			sql += " DESC";

		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		// Find transactions with no corresponding row in BlockTransactions
		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams.toArray())) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch approval-pending transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch approval-pending transactions from repository", e);
		}
	}

	@Override
	public List<TransactionData> getApprovalPendingTransactions(int blockHeight) throws DataException {
		String sql = "SELECT signature FROM Transactions "
			+ "JOIN Groups on Groups.group_id = Transactions.tx_group_id "
			+ "WHERE Transactions.approval_status = ? "
			+ "AND Transactions.block_height < ? - Groups.min_block_delay";

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, ApprovalStatus.PENDING.value, blockHeight)) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch approval-expiring transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch approval-expiring transactions from repository", e);
		}
	}

	@Override
	public List<TransactionData> getApprovalExpiringTransactions(int blockHeight) throws DataException {
		String sql = "SELECT signature FROM Transactions "
			+ "JOIN Groups on Groups.group_id = Transactions.tx_group_id "
			+ "WHERE Transactions.approval_status = ? "
			+ "AND Transactions.block_height < ? - Groups.max_block_delay";

		List<TransactionData> transactions = new ArrayList<TransactionData>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, ApprovalStatus.PENDING.value, blockHeight)) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch approval-expiring transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch approval-expiring transactions from repository", e);
		}
	}

	@Override
	public List<TransactionData> getApprovalTransactionDecidedAtHeight(int approvalHeight) throws DataException {
		final String sql = "SELECT signature from Transactions WHERE approval_height = ?";

		List<TransactionData> transactions = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, approvalHeight)) {
			if (resultSet == null)
				return transactions;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.fromSignature(signature);

				if (transactionData == null)
					// Something inconsistent with the repository
					throw new DataException("Unable to fetch approval-decided transaction from repository?");

				transactions.add(transactionData);
			} while (resultSet.next());

			return transactions;
		} catch (SQLException | DataException e) {
			throw new DataException("Unable to fetch approval-decided transactions from repository", e);
		}
	}

	@Override
	public GroupApprovalTransactionData getLatestApproval(byte[] pendingSignature, byte[] adminPublicKey) throws DataException {
		String sql = "SELECT signature FROM GroupApprovalTransactions "
			+ "NATURAL JOIN Transactions "
			+ "WHERE pending_signature = ? AND admin = ? AND block_height IS NOT NULL "
			+ "ORDER BY creation DESC, signature DESC LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, pendingSignature, adminPublicKey)) {
			if (resultSet == null)
				return null;

			byte[] signature = resultSet.getBytes(1);

			return (GroupApprovalTransactionData) this.fromSignature(signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest transaction group-admin approval from repository", e);
		}
	}

	@Override
	public GroupApprovalData getApprovalData(byte[] pendingSignature) throws DataException {
		// Fetch latest approval data for pending transaction's signature
		// NOT simply number of GROUP_APPROVAL transactions as some may be rejecting transaction, or changed opinions
		// Also make sure that GROUP_APPROVAL transaction's admin is still an admin of group

		// Sub-query SQL to find latest GroupApprovalTransaction relating to passed pending signature
		String latestApprovalSql = "SELECT pending_signature, admin, approval, creation, signature FROM GroupApprovalTransactions "
				+ "NATURAL JOIN Transactions WHERE pending_signature = ? AND block_height IS NOT NULL";

		String sql = "SELECT GAT.admin, GAT.approval FROM "
				+ "(" + latestApprovalSql + ") AS GAT "
				+ "LEFT OUTER JOIN (" + latestApprovalSql + ") AS NewerGAT "
					+ "ON NewerGAT.admin = GAT.admin AND (NewerGAT.creation > GAT.creation OR (NewerGAT.creation = GAT.creation AND NewerGat.signature > GAT.signature)) "
				+ "JOIN Transactions AS PendingTransactions ON PendingTransactions.signature = GAT.pending_signature "
				+ "LEFT OUTER JOIN Accounts ON Accounts.public_key = GAT.admin "
				+ "LEFT OUTER JOIN GroupAdmins ON GroupAdmins.admin = Accounts.account AND GroupAdmins.group_id = PendingTransactions.tx_group_id "
				+ "WHERE NewerGAT.admin IS NULL";

		GroupApprovalData groupApprovalData = new GroupApprovalData();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, pendingSignature, pendingSignature)) {
			if (resultSet == null)
				return groupApprovalData;

			do {
				byte[] adminPublicKey = resultSet.getBytes(1);
				boolean approval = resultSet.getBoolean(2);

				if (approval)
					groupApprovalData.approvingAdmins.add(adminPublicKey);
				else
					groupApprovalData.rejectingAdmins.add(adminPublicKey);
			} while (resultSet.next());

			return groupApprovalData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest transaction group-approval decisions from repository", e);
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
		String sql = "SELECT signature FROM UnconfirmedTransactions ";

		sql += "ORDER BY creation";
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
	public void updateBlockHeight(byte[] signature, Integer blockHeight) throws DataException {
		HSQLDBSaver saver = new HSQLDBSaver("Transactions");

		saver.bind("signature", signature).bind("block_height", blockHeight);

		try {
			saver.execute(repository);
		} catch (SQLException e) {
			throw new DataException("Unable to update transaction's block height in repository", e);
		}
	}

	@Override
	public void updateApprovalHeight(byte[] signature, Integer approvalHeight) throws DataException {
		HSQLDBSaver saver = new HSQLDBSaver("Transactions");

		saver.bind("signature", signature).bind("approval_height", approvalHeight);

		try {
			saver.execute(repository);
		} catch (SQLException e) {
			throw new DataException("Unable to update transaction's approval height in repository", e);
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

		// Do not include "block_height" or "approval_height" as they are modified a different way

		saver.bind("signature", transactionData.getSignature()).bind("reference", transactionData.getReference())
			.bind("type", transactionData.getType().value)
			.bind("creator", transactionData.getCreatorPublicKey()).bind("creation", new Timestamp(transactionData.getTimestamp()))
			.bind("fee", transactionData.getFee()).bind("milestone_block", null).bind("tx_group_id", transactionData.getTxGroupId())
			.bind("approval_status", transactionData.getApprovalStatus().value);

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
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof DataException)
				throw (DataException) e.getCause();

			throw new DataException("Exception during save of transaction type [" + type.name() + "] into HSQLDB repository");
		} catch (IllegalAccessException | IllegalArgumentException e) {
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

		// If transaction subclass has a "delete" method - call that now
		TransactionType type = transactionData.getType();
		if (subclassInfos[type.value].deleteMethod != null) {
			HSQLDBTransactionRepository txRepository = repositoryByTxType[type.value];

			try {
				subclassInfos[type.value].deleteMethod.invoke(txRepository, transactionData);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof DataException)
					throw (DataException) e.getCause();

				throw new DataException("Exception during delete of transaction type [" + type.name() + "] from HSQLDB repository");
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new DataException("Unsupported transaction type [" + type.name() + "] during delete from HSQLDB repository");
			}
		}
	}

}
