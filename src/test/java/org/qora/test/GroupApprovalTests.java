package org.qora.test;

import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.block.BlockGenerator;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transaction.CreateGroupTransaction;
import org.qora.transaction.PaymentTransaction;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;

import static org.junit.Assert.*;

import java.math.BigDecimal;

public class GroupApprovalTests extends Common {

	/** Check that a tx type that doesn't need approval doesn't accept txGroupId apart from NO_GROUP */
	@Test
	public void testNonApprovalTxGroupId() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockChain.validate();

			TransactionData transactionData = buildPayment(repository, Group.NO_GROUP);
			Transaction transaction = new PaymentTransaction(repository, transactionData);
			assertEquals(ValidationResult.OK, transaction.isValidUnconfirmed());

			int groupId = createGroup(repository);

			transactionData = buildPayment(repository, groupId);
			transaction = new PaymentTransaction(repository, transactionData);
			assertEquals(ValidationResult.INVALID_TX_GROUP_ID, transaction.isValidUnconfirmed());
		}
	}

	private PaymentTransactionData buildPayment(Repository repository, int txGroupId) throws DataException {
		long timestamp = System.currentTimeMillis() - 1000L;
		byte[] reference = repository.getAccountRepository().getLastReference(v2testAddress);
		byte[] senderPublicKey = v2testPublicKey;
		String recipient = v2testAddress;
		BigDecimal amount = BigDecimal.ONE.setScale(8);
		BigDecimal fee = BigDecimal.ONE.setScale(8);

		return new PaymentTransactionData(timestamp, txGroupId, reference, senderPublicKey, recipient, amount, fee);
	}

	private int createGroup(Repository repository) throws DataException {
		long timestamp = System.currentTimeMillis() - 1000L;
		int txGroupId = Group.NO_GROUP;
		byte[] reference = repository.getAccountRepository().getLastReference(v2testAddress);
		byte[] creatorPublicKey = v2testPublicKey;
		String owner = v2testAddress;
		String groupName = "test-group";
		String description = "test group description";
		boolean isOpen = false;
		ApprovalThreshold approvalThreshold = ApprovalThreshold.ONE;
		int minimumBlockDelay = 0;
		int maximumBlockDelay = 1440;
		Integer groupId = null;
		BigDecimal fee = BigDecimal.ONE.setScale(8);
		byte[] signature = null;

		TransactionData transactionData = new CreateGroupTransactionData(timestamp, txGroupId, reference, creatorPublicKey, owner, groupName, description,
				isOpen, approvalThreshold, minimumBlockDelay, maximumBlockDelay, groupId, fee, signature);
		Transaction transaction = new CreateGroupTransaction(repository, transactionData);

		// Sign transaction
		PrivateKeyAccount signer = new PrivateKeyAccount(repository, v2testPrivateKey);
		transaction.sign(signer);

		// Add to unconfirmed
		if (!transaction.isSignatureValid())
			throw new RuntimeException("CREATE_GROUP transaction's signature invalid");

		ValidationResult result = transaction.isValidUnconfirmed();
		if (result != ValidationResult.OK)
			throw new RuntimeException(String.format("CREATE_GROUP transaction invalid: %s", result.name()));

		repository.getTransactionRepository().save(transactionData);
		repository.getTransactionRepository().unconfirmTransaction(transactionData);
		repository.saveChanges();

		// Generate block
		BlockGenerator.generateTestingBlock(repository, signer);

		// Return assigned groupId
		transactionData = repository.getTransactionRepository().fromSignature(transactionData.getSignature());
		return ((CreateGroupTransactionData) transactionData).getGroupId();
	}

}
