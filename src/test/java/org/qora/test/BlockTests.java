package org.qora.test;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.Block;
import org.qora.block.BlockGenerator;
import org.qora.block.GenesisBlock;
import org.qora.data.at.ATStateData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.test.common.TransactionUtils;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.transform.block.BlockTransformer;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;
import org.qora.utils.Triple;

import static org.junit.Assert.*;

public class BlockTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGenesisBlockTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			GenesisBlock block = GenesisBlock.getInstance(repository);

			assertNotNull(block);
			assertTrue(block.isSignatureValid());
			// only true if blockchain is empty
			// assertTrue(block.isValid());

			List<Transaction> transactions = block.getTransactions();
			assertNotNull(transactions);

			byte[] lastGenesisSignature = null;
			for (Transaction transaction : transactions) {
				assertNotNull(transaction);

				TransactionData transactionData = transaction.getTransactionData();

				if (transactionData.getType() != Transaction.TransactionType.GENESIS)
					continue;

				assertTrue(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
				assertTrue(transaction.isSignatureValid());
				assertEquals(Transaction.ValidationResult.OK, transaction.isValid());

				lastGenesisSignature = transactionData.getSignature();
			}

			// Attempt to load last GENESIS transaction directly from database
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(lastGenesisSignature);
			assertNotNull(transactionData);

			assertEquals(Transaction.TransactionType.GENESIS, transactionData.getType());
			assertTrue(transactionData.getFee().compareTo(BigDecimal.ZERO) == 0);
			assertNull(transactionData.getReference());

			Transaction transaction = Transaction.fromData(repository, transactionData);
			assertNotNull(transaction);

			assertTrue(transaction.isSignatureValid());
			assertEquals(Transaction.ValidationResult.OK, transaction.isValid());
		}
	}

	@Test
	public void testBlockSerialization() throws DataException, TransformationException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount signingAccount = Common.getTestAccount(repository, "alice");

			// TODO: Fill block with random, valid transactions of every type (except GENESIS, ACCOUNT_FLAGS or AT)
			// This isn't as trivial as it seems as some transactions rely on others.
			// e.g. CANCEL_ASSET_ORDER needs a prior CREATE_ASSET_ORDER
			for (Transaction.TransactionType txType : Transaction.TransactionType.values()) {
				if (txType == TransactionType.GENESIS || txType == TransactionType.ACCOUNT_FLAGS || txType == TransactionType.AT)
					continue;

				TransactionData transactionData = TransactionUtils.randomTransaction(repository, signingAccount, txType, true);
				Transaction transaction = Transaction.fromData(repository, transactionData);
				transaction.sign(signingAccount);

				Transaction.ValidationResult validationResult = transaction.importAsUnconfirmed();
				if (validationResult != Transaction.ValidationResult.OK)
					fail(String.format("Invalid (%s) test transaction, type %s", validationResult.name(), txType.name()));
			}

			// We might need to wait until transactions' timestamps are valid for the block we're about to generate
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
			}

			BlockGenerator.generateTestingBlock(repository, signingAccount);

			BlockData blockData = repository.getBlockRepository().getLastBlock();
			Block block = new Block(repository, blockData);
			assertTrue(block.isSignatureValid());

			byte[] bytes = BlockTransformer.toBytes(block);

			assertEquals(BlockTransformer.getDataLength(block), bytes.length);

			Triple<BlockData, List<TransactionData>, List<ATStateData>> blockInfo = BlockTransformer.fromBytes(bytes);

			// Compare transactions
			List<TransactionData> deserializedTransactions = blockInfo.getB();
			assertEquals("Transaction count differs", blockData.getTransactionCount(), deserializedTransactions.size());

			for (int i = 0; i < blockData.getTransactionCount(); ++i) {
				TransactionData deserializedTransactionData = deserializedTransactions.get(i);
				Transaction originalTransaction = block.getTransactions().get(i);
				TransactionData originalTransactionData = originalTransaction.getTransactionData();

				assertEquals("Transaction signature differs", Base58.encode(originalTransactionData.getSignature()), Base58.encode(deserializedTransactionData.getSignature()));
				assertEquals("Transaction declared length differs", TransactionTransformer.getDataLength(originalTransactionData), TransactionTransformer.getDataLength(deserializedTransactionData));
				assertEquals("Transaction serialized length differs", TransactionTransformer.toBytes(originalTransactionData).length, TransactionTransformer.toBytes(deserializedTransactionData).length);
			}
		}
	}

}
