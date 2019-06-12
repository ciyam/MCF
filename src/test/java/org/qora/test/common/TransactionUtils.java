package org.qora.test.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockGenerator;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.test.common.transaction.TestTransaction;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transaction.Transaction.ValidationResult;

public class TransactionUtils {

	/** Signs transaction using given account and imports into unconfirmed pile. */
	public static void signAsUnconfirmed(Repository repository, TransactionData transactionData, PrivateKeyAccount signingAccount) throws DataException {
		Transaction transaction = Transaction.fromData(repository, transactionData);
		transaction.sign(signingAccount);

		// Add to unconfirmed
		assertTrue("Transaction's signature should be valid", transaction.isSignatureValid());

		// We might need to wait until transaction's timestamp is valid for the block we're about to generate
		try {
			Thread.sleep(1L);
		} catch (InterruptedException e) {
		}

		ValidationResult result = transaction.importAsUnconfirmed();
		assertEquals("Transaction invalid", ValidationResult.OK, result);
	}

	/** Signs transaction using given account and forges a new block, using "alice" account. */
	public static void signAndForge(Repository repository, TransactionData transactionData, PrivateKeyAccount signingAccount) throws DataException {
		signAsUnconfirmed(repository, transactionData, signingAccount);

		// Generate block
		PrivateKeyAccount generatorAccount = Common.getTestAccount(repository, "alice");
		BlockGenerator.generateTestingBlock(repository, generatorAccount);
	}

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, TransactionType txType, boolean wantValid) throws DataException {
		try {
			Class <?> clazz = Class.forName(String.join("", TestTransaction.class.getPackage().getName(), ".", txType.className, "TestTransaction"));

			try {
				Method method = clazz.getDeclaredMethod("randomTransaction", Repository.class, PrivateKeyAccount.class, boolean.class);
				return (TransactionData) method.invoke(null, repository, account, wantValid);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(String.format("Transaction subclass constructor not found for transaction type \"%s\"", txType.name()), e);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(String.format("Transaction subclass not found for transaction type \"%s\"", txType.name()), e);
		}
	}

}
