package api.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import io.swagger.v3.oas.annotations.Operation;

@Path("/")
public class BlockExplorerResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/block-explorer.html")
	@Operation(hidden = true)
	public String getBlockExplorer() {
		ClassLoader loader = this.getClass().getClassLoader();
		try (InputStream inputStream = loader.getResourceAsStream("block-explorer.html")) {
			if (inputStream == null)
				return "block-explorer.html resource not found";

			return new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			return "Error reading block-explorer.html resource";
		}
	}

}
