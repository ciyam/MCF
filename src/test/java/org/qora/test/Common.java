package org.qora.test;

import org.junit.jupiter.api.BeforeAll;
import org.qora.controller.Controller;
import org.qora.repository.DataException;
import org.qora.repository.RepositoryFactory;
import org.qora.repository.RepositoryManager;
import org.qora.repository.hsqldb.HSQLDBRepositoryFactory;
import org.junit.jupiter.api.AfterAll;

public class Common {

	@BeforeAll
	public static void setRepository() throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(Controller.connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);
	}

	@AfterAll
	public static void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

}
