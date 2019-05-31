package org.qora.repository.hsqldb.transaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.hsqldb.HSQLDBRepository;
import org.qora.repository.hsqldb.HSQLDBSaver;
import org.qora.transaction.Transaction.ApprovalStatus;

public class HSQLDBGroupApprovalTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBGroupApprovalTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT pending_signature, approval, prior_reference FROM GroupApprovalTransactions WHERE signature = ?", signature)) {
			if (resultSet == null)
				return null;

			byte[] pendingSignature = resultSet.getBytes(1);
			boolean approval = resultSet.getBoolean(2);
			byte[] priorReference = resultSet.getBytes(3);

			return new GroupApprovalTransactionData(timestamp, txGroupId, reference, creatorPublicKey, pendingSignature, approval, priorReference,
					fee, approvalStatus, height, signature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group approval transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		GroupApprovalTransactionData groupApprovalTransactionData = (GroupApprovalTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("GroupApprovalTransactions");

		saveHelper.bind("signature", groupApprovalTransactionData.getSignature()).bind("admin", groupApprovalTransactionData.getAdminPublicKey())
				.bind("pending_signature", groupApprovalTransactionData.getPendingSignature()).bind("approval", groupApprovalTransactionData.getApproval())
				.bind("prior_reference", groupApprovalTransactionData.getPriorReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group approval transaction into repository", e);
		}
	}

}
