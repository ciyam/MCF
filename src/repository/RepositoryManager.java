package repository;

public abstract class RepositoryManager {

	private static RepositoryFactory repositoryFactory;

	public static void setRepositoryFactory(RepositoryFactory newRepositoryFactory) {
		repositoryFactory = newRepositoryFactory;
	}

	public static Repository getRepository() throws DataException {
		return repositoryFactory.getRepository();
	}

}
