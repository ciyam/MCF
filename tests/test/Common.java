package test;

import org.junit.jupiter.api.BeforeAll;

import controller.Controller;

import org.junit.jupiter.api.AfterAll;

import repository.DataException;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;

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
