package api.resource;

import globalization.Translator;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.lang.reflect.Method;
import java.util.Locale;

import javax.ws.rs.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import api.ApiError;
import api.ApiErrorMessage;
import api.ApiErrors;
import api.ApiService;

public class AnnotationPostProcessor implements ReaderListener {

	private static final Logger LOGGER = LogManager.getLogger(AnnotationPostProcessor.class);

	@Override
	public void beforeScan(Reader reader, OpenAPI openAPI) {
	}

	@Override
	public void afterScan(Reader reader, OpenAPI openAPI) {
		// Populate Components section with reusable parameters, like "limit" and "offset"
		// We take the reusable parameters from AdminResource.globalParameters path "/admin/unused"
		Components components = openAPI.getComponents();

		PathItem globalParametersPathItem = openAPI.getPaths().get("/admin/unused");

		if (globalParametersPathItem != null) {
			for (Parameter parameter : globalParametersPathItem.getGet().getParameters())
				components.addParameters(parameter.getName(), parameter);

			openAPI.getPaths().remove("/admin/unused");
		}

		// Search all ApiService resources (classes) for @ApiErrors annotations
		// to generate corresponding openAPI operation responses.
		for (Class<?> clazz : ApiService.getInstance().getResources()) {
			Path classPath = clazz.getAnnotation(Path.class);
			if (classPath == null)
				continue;

			String classPathString = classPath.value();
			if (classPathString.charAt(0) != '/')
				classPathString = "/" + classPathString;

			for (Method method : clazz.getDeclaredMethods()) {
				ApiErrors apiErrors = method.getAnnotation(ApiErrors.class);
				if (apiErrors == null)
					continue;

				LOGGER.info("Found @ApiErrors annotation on " + clazz.getSimpleName() + "." + method.getName());
				PathItem pathItem = getPathItemFromMethod(openAPI, classPathString, method);

				for (Operation operation : pathItem.readOperations())
					for (ApiError apiError : apiErrors.value())
						addApiErrorResponse(operation, apiError);
			}
		}
	}

	private PathItem getPathItemFromMethod(OpenAPI openAPI, String classPathString, Method method) {
		Path path = method.getAnnotation(Path.class);
		if (path == null)
			throw new RuntimeException("API method has no @Path annotation?");

		String pathString = path.value();
		return openAPI.getPaths().get(classPathString + pathString);
	}

	private void addApiErrorResponse(Operation operation, ApiError apiError) {
		String statusCode = Integer.toString(apiError.getStatus()) + " " + apiError.name();

		// Create response for this HTTP response code if it doesn't already exist
		ApiResponse apiResponse = operation.getResponses().get(statusCode);
		if (apiResponse == null) {
			Schema<?> errorMessageSchema = ModelConverters.getInstance().readAllAsResolvedSchema(ApiErrorMessage.class).schema;
			MediaType mediaType = new MediaType().schema(errorMessageSchema);
			Content content = new Content().addMediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON, mediaType);
			apiResponse = new ApiResponse().content(content);
			operation.getResponses().addApiResponse(statusCode, apiResponse);
		}

		// Add this specific ApiError code as an example
		int apiErrorCode = apiError.getCode();
		String lang = Locale.getDefault().getLanguage();
		ApiErrorMessage apiErrorMessage = new ApiErrorMessage(apiErrorCode, Translator.INSTANCE.translate("ApiError", lang, apiError.name()));
		Example example = new Example().value(apiErrorMessage);

		// XXX: addExamples(..) is not working in Swagger 2.0.4. This bug is referenced in https://github.com/swagger-api/swagger-ui/issues/2651
		// Replace the call to .setExample(..) by .addExamples(..) when the bug is fixed.
		apiResponse.getContent().get(javax.ws.rs.core.MediaType.APPLICATION_JSON).setExample(example);
		//apiResponse.getContent().get(javax.ws.rs.core.MediaType.APPLICATION_JSON).addExamples(Integer.toString(apiErrorCode), example);
	}

}
