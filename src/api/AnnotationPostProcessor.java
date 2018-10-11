
package api;

import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.models.OpenAPI;

public class AnnotationPostProcessor implements ReaderListener {

	@Override
	public void beforeScan(Reader reader, OpenAPI openAPI) {}

	@Override
	public void afterScan(Reader reader, OpenAPI openAPI) {
		// TODO: use context path and keys from "x-translation" extension annotations
		// to translate "descriptions" and finally remove "x-translation" extensions
		// from output.
	}
	
}
