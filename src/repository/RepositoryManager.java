package repository;

public abstract class RepositoryManager {

	private static RepositoryFactory repositoryFactory = null;

	public static void setRepositoryFactory(RepositoryFactory newRepositoryFactory) {
		repositoryFactory = newRepositoryFactory;
	}

	public static Repository getRepository() throws DataException {
		if (repositoryFactory == null)
			throw new DataException("No repository available");

		return repositoryFactory.getRepository();
	}

	public static void closeRepositoryFactory() throws DataException {
		repositoryFactory.close();
		repositoryFactory = null;
	}

}
