package org.qora.test.group;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockGenerator;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.group.Group.ApprovalThreshold;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.BlockUtils;
import org.qora.test.common.Common;
import org.qora.test.common.GroupUtils;
import org.qora.test.common.TransactionUtils;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.ValidationResult;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;

public class GroupApprovalTests extends Common {

	private static final BigDecimal amount = BigDecimal.valueOf(5000L).setScale(8);
	private static final BigDecimal fee = BigDecimal.ONE.setScale(8);

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	/** Check that a transaction type that doesn't need approval doesn't accept txGroupId apart from NO_GROUP */
	public void testNonApprovalTxGroupId() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = buildPaymentTransaction(repository, "alice", "bob", amount, Group.NO_GROUP);
			assertEquals(ValidationResult.OK, transaction.isValidUnconfirmed());

			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.NONE, 0, 10);

			transaction = buildPaymentTransaction(repository, "alice", "bob", amount, groupId);
			assertEquals(ValidationResult.INVALID_TX_GROUP_ID, transaction.isValidUnconfirmed());
		}
	}

	@Test
	/** Check that a transaction, that requires approval, updates references and fees properly. */
	public void testReferencesAndFees() throws DataException {
		final int minBlockDelay = 5;
		final int maxBlockDelay = 20;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");

			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			GroupUtils.joinGroup(repository, "bob", groupId);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			byte[] bobOriginalReference = bobAccount.getLastReference();

			BigDecimal aliceOriginalBalance = aliceAccount.getConfirmedBalance(Asset.QORA);
			BigDecimal bobOriginalBalance = bobAccount.getConfirmedBalance(Asset.QORA);

			BigDecimal blockReward = BlockUtils.getNextBlockReward(repository);
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndForge(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Bob's last-reference should have changed, even though the transaction itself hasn't been approved yet
			byte[] bobPostAssetReference = bobAccount.getLastReference();
			assertFalse("reference should have changed", Arrays.equals(bobOriginalReference, bobPostAssetReference));

			// Bob's balance should have the fee removed, even though the transaction itself hasn't been approved yet
			BigDecimal bobPostAssetBalance = bobAccount.getConfirmedBalance(Asset.QORA);
			Common.assertEqualBigDecimals("approval-pending transaction creator's balance incorrect", bobOriginalBalance.subtract(fee), bobPostAssetBalance);

			// Transaction fee should have ended up in forging account
			BigDecimal alicePostAssetBalance = aliceAccount.getConfirmedBalance(Asset.QORA);
			Common.assertEqualBigDecimals("block forger's balance incorrect", aliceOriginalBalance.add(blockReward).add(fee), alicePostAssetBalance);

			// Have Bob do a non-approval transaction to change his last-reference
			Transaction bobPaymentTransaction = buildPaymentTransaction(repository, "bob", "chloe", amount, Group.NO_GROUP);
			TransactionUtils.signAsUnconfirmed(repository, bobPaymentTransaction.getTransactionData(), bobAccount);
			BlockGenerator.generateTestingBlock(repository, aliceAccount);

			byte[] bobPostPaymentReference = bobAccount.getLastReference();
			assertFalse("reference should have changed", Arrays.equals(bobPostAssetReference, bobPostPaymentReference));

			// Have Alice approve Bob's approval-needed transaction
			GroupUtils.approveTransaction(repository, "alice", bobAssetTransaction.getTransactionData().getSignature(), true);

			// Now forge a few blocks so transaction is approved
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockGenerator.generateTestingBlock(repository, aliceAccount);

			// Confirm transaction now approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.APPROVED, approvalStatus);

			// Check Bob's last reference hasn't been changed by transaction approval
			byte[] bobPostApprovalReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobPostApprovalReference));

			// Ok, now unwind/orphan all the above to double-check

			// Orphan blocks that decided transaction approval
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference is still correct
			byte[] bobReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobReference));

			// Orphan block containing Alice's group-approval transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference is still correct
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobReference));

			// Orphan block containing Bob's non-approval payment transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference has reverted to pre-payment value
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be pre-payment", Arrays.equals(bobPostAssetReference, bobReference));

			// Orphan block containing Bob's issue-asset approval-needed transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference has reverted to original value
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be pre-payment", Arrays.equals(bobOriginalReference, bobReference));

			// Also check Bob's balance is back to original value
			BigDecimal bobBalance = bobAccount.getConfirmedBalance(Asset.QORA);
			Common.assertEqualBigDecimals("reverted balance doesn't match original", bobOriginalBalance, bobBalance);
		}
	}

	private Transaction buildPaymentTransaction(Repository repository, String sender, String recipient, BigDecimal amount, int txGroupId) throws DataException {
		PrivateKeyAccount sendingAccount = Common.getTestAccount(repository, sender);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		byte[] reference = sendingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, sendingAccount.getPublicKey(), fee, null);
		PaymentTransactionData transactionData = new PaymentTransactionData(baseTransactionData, recipientAccount.getAddress(), amount);

		return Transaction.fromData(repository, transactionData);
	}

	private Transaction buildIssueAssetTransaction(Repository repository, String testAccountName, int txGroupId) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, testAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, account.getPublicKey(), fee, null);
		TransactionData transactionData = new IssueAssetTransactionData(baseTransactionData, account.getAddress(), "test asset", "test asset desc", 1000L, true, "{}");

		return Transaction.fromData(repository, transactionData);
	}

}
