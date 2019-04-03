package org.qora.test.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockGenerator;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;

public class TransactionUtils {

	public static void signAndForge(Repository repository, TransactionData transactionData, PrivateKeyAccount signingAccount) throws DataException {
		Transaction transaction = Transaction.fromData(repository, transactionData);
		transaction.sign(signingAccount);

		// Add to unconfirmed
		assertTrue("Transaction's signature should be valid", transaction.isSignatureValid());

		ValidationResult result = transaction.isValidUnconfirmed();
		assertEquals("Transaction invalid", ValidationResult.OK, result);

		repository.getTransactionRepository().save(transactionData);
		repository.getTransactionRepository().unconfirmTransaction(transactionData);
		repository.saveChanges();

		// Generate block
		BlockGenerator.generateTestingBlock(repository, signingAccount);
	}

}
