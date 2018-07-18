package api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

@Path("blocks")
@Produces(MediaType.APPLICATION_JSON)
public class BlocksResource {

	@GET
	@Path("/height")
	public static String getHeight() 
	{
		try (final Repository repository = RepositoryManager.getRepository()) {
			return String.valueOf(repository.getBlockRepository().getBlockchainHeight());
		} catch (Exception e) {
			throw new WebApplicationException("What happened?");
		}
	}

}
