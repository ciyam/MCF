package org.qora.repository;

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

	public static Repository tryRepository() throws DataException {
		if (repositoryFactory == null)
			throw new DataException("No repository available");

		return repositoryFactory.tryRepository();
	}

	public static void closeRepositoryFactory() throws DataException {
		repositoryFactory.close();
		repositoryFactory = null;
	}

	public static void backup(boolean quick) {
		try (final Repository repository = getRepository()) {
			repository.backup(quick);
		} catch (DataException e) {
			// Backup is best-effort so don't complain
		}
	}

}
