package org.qora.test;

import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.test.common.TransactionUtils;
import org.qora.transaction.Transaction;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;

import com.google.common.hash.HashCode;

import static org.junit.Assert.*;

import org.junit.Before;

public class SerializationTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testTransactions() throws DataException, TransformationException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount signingAccount = Common.getTestAccount(repository, "alice");

			// Check serialization/deserialization of transactions of every type (except GENESIS, ACCOUNT_FLAGS or AT)
			for (Transaction.TransactionType txType : Transaction.TransactionType.values()) {
				switch (txType) {
					case GENESIS:
					case ACCOUNT_FLAGS:
					case AT:
					case DELEGATION:
					case SUPERNODE:
					case AIRDROP:
						continue;

					default:
						// fall-through
				}

				TransactionData transactionData = TransactionUtils.randomTransaction(repository, signingAccount, txType, true);
				Transaction transaction = Transaction.fromData(repository, transactionData);
				transaction.sign(signingAccount);

				final int claimedLength = TransactionTransformer.getDataLength(transactionData);
				byte[] serializedTransaction = TransactionTransformer.toBytes(transactionData);
				assertEquals(String.format("Serialized %s transaction length differs from declared length", txType.name()), claimedLength, serializedTransaction.length);

				TransactionData deserializedTransactionData = TransactionTransformer.fromBytes(serializedTransaction);
				// Re-sign
				Transaction deserializedTransaction = Transaction.fromData(repository, deserializedTransactionData);
				deserializedTransaction.sign(signingAccount);
				assertEquals(String.format("Deserialized %s transaction signature differs", txType.name()), Base58.encode(transactionData.getSignature()), Base58.encode(deserializedTransactionData.getSignature()));

				// Re-serialize to check new length and bytes
				final int reclaimedLength = TransactionTransformer.getDataLength(deserializedTransactionData);
				assertEquals(String.format("Reserialized %s transaction declared length differs", txType.name()), claimedLength, reclaimedLength);

				byte[] reserializedTransaction = TransactionTransformer.toBytes(deserializedTransactionData);
				assertEquals(String.format("Reserialized %s transaction bytes differ", txType.name()), HashCode.fromBytes(serializedTransaction).toString(), HashCode.fromBytes(reserializedTransaction).toString());
			}
		}
	}

}