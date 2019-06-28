package api;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import io.swagger.v3.oas.annotations.Operation;

@Path("/")
public class BlockExplorerResource {

	@Context
	HttpServletRequest request;

	public BlockExplorerResource() {
	}

	@GET
	@Path("/block-explorer.html")
	@Operation(hidden = true)
	public String getBlockExplorer() {
		try {
			byte[] htmlBytes = Files.readAllBytes(FileSystems.getDefault().getPath("block-explorer.html"));
			return new String(htmlBytes, "UTF-8");
		} catch (IOException e) {
			return "block-explorer.html not found";
		}
	}

}
