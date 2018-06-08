package repository;

public abstract class RepositoryManager {

	private static Repository repository;

	public static void setRepository(Repository newRepository) {
		repository = newRepository;
	}

	public static TransactionRepository getTransactionRepository() {
		return repository.transactionRepository;
	}

	public static BlockRepository getBlockRepository() {
		return repository.blockRepository;
	}

}
