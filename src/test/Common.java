package test;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import repository.DataException;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;

public class Common {

	public static final String connectionUrl = "jdbc:hsqldb:file:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true;sql.pad_space=false";

	@BeforeClass
	public static void setRepository() throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);
	}

	@AfterClass
	public static void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

}
