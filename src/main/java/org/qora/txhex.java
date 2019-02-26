package org.qora;
import org.qora.block.BlockChain;
import org.qora.controller.Controller;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;
import org.qora.utils.Base58;

import com.google.common.hash.HashCode;

public class txhex {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: txhex <base58-tx-signature>");
			System.exit(1);
		}

		byte[] signature = Base58.decode(args[0]);

		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(Controller.connectionUrl);
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			System.err.println("Couldn't connect to repository: " + e.getMessage());
			System.exit(2);
		}

		try {
			BlockChain.validate();
		} catch (DataException e) {
			System.err.println("Couldn't validate repository: " + e.getMessage());
			System.exit(2);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			byte[] bytes = TransactionTransformer.toBytes(transactionData);
			System.out.println(HashCode.fromBytes(bytes).toString());
		} catch (DataException | TransformationException e) {
			e.printStackTrace();
		}

		try {
			RepositoryManager.closeRepositoryFactory();
		} catch (DataException e) {
			e.printStackTrace();
		}
	}

}
