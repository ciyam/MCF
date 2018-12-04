import com.google.common.hash.HashCode;

import data.transaction.TransactionData;
import qora.block.BlockChain;
import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;
import transform.TransformationException;
import transform.transaction.TransactionTransformer;
import utils.Base58;

public class txhex {

	public static final String connectionUrl = "jdbc:hsqldb:file:db/test;create=true";

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: txhex <base58-tx-signature>");
			System.exit(1);
		}

		byte[] signature = Base58.decode(args[0]);

		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
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
