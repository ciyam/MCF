import controller.Controller;
import data.block.BlockData;
import qora.block.Block;
import qora.block.BlockChain;
import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;

public class orphan {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: orphan <new-blockchain-tip-height>");
			System.exit(1);
		}

		int targetHeight = Integer.parseInt(args[0]);

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
			for (int height = repository.getBlockRepository().getBlockchainHeight(); height > targetHeight; --height) {
				System.out.println("Orphaning block " + height);

				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				Block block = new Block(repository, blockData);
				block.orphan();
				repository.saveChanges();
			}
		} catch (DataException e) {
			e.printStackTrace();
		}

		try {
			RepositoryManager.closeRepositoryFactory();
		} catch (DataException e) {
			e.printStackTrace();
		}
	}

}
