package org.qora.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.transaction.ArbitraryTransaction;
import org.qora.transaction.Transaction.TransactionType;

public class ArbitraryDataManager extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataManager.class);
	private static final List<TransactionType> ARBITRARY_TX_TYPE = Arrays.asList(TransactionType.ARBITRARY);

	private static ArbitraryDataManager instance;

	private volatile boolean isStopping = false;

	private ArbitraryDataManager() {
	}

	public static ArbitraryDataManager getInstance() {
		if (instance == null)
			instance = new ArbitraryDataManager();

		return instance;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Arbitrary Data Manager");

		try {
			while (!isStopping) {
				Thread.sleep(2000);

				// Any arbitrary transactions we want to fetch data for?
				try (final Repository repository = RepositoryManager.getRepository()) {
					List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, null, ARBITRARY_TX_TYPE, null, null, ConfirmationStatus.BOTH, null, null, true);
					if (signatures == null || signatures.isEmpty())
						continue;

					// Filter out those that already have local data
					signatures.removeIf(signature -> hasLocalData(repository, signature));

					if (signatures.isEmpty())
						continue;

					// Pick one at random
					final int index = new Random().nextInt(signatures.size());
					byte[] signature = signatures.get(index);

					Controller.getInstance().fetchArbitraryData(signature);
				} catch (DataException e) {
					LOGGER.error("Repository issue when fetching arbitrary transaction data", e);
				}
			}
		} catch (InterruptedException e) {
			return;
		}
	}

	public void shutdown() {
		isStopping = true;
		this.interrupt();
	}

	private boolean hasLocalData(final Repository repository, final byte[] signature) {
		try {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null || !(transactionData instanceof ArbitraryTransactionData))
				return true;

			ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);

			return arbitraryTransaction.isDataLocal();
		} catch (DataException e) {
			LOGGER.error("Repository issue when checking arbitrary transaction's data is local", e);
			return true;
		}
	}

}
