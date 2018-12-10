package api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import globalization.ContextPaths;
import globalization.Translator;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class AnnotationPostProcessor implements ReaderListener {

	private static final Logger LOGGER = LogManager.getLogger(AnnotationPostProcessor.class);

	private class ContextInformation {
		public String path;
		public Map<String, String> keys;
	}

	private final Translator translator;
	private final ApiErrorFactory apiErrorFactory;
	
	public AnnotationPostProcessor() {
		this(Translator.getInstance(), ApiErrorFactory.getInstance());
	}
	
	public AnnotationPostProcessor(Translator translator, ApiErrorFactory apiErrorFactory) {
		this.translator = translator;
		this.apiErrorFactory = apiErrorFactory;
	}
	
	@Override
	public void beforeScan(Reader reader, OpenAPI openAPI) {
	}

	@Override
	public void afterScan(Reader reader, OpenAPI openAPI) {
		// Populate Components section with reusable parameters, like "limit" and "offset"
		// We take the reusable parameters from AdminResource.globalParameters path "/admin/dud"
		Components components = openAPI.getComponents();
		PathItem globalParametersPathItem = openAPI.getPaths().get("/admin/dud");
		if (globalParametersPathItem != null)
			for (Parameter parameter : globalParametersPathItem.getGet().getParameters())
				components.addParameters(parameter.getName(), parameter);

		// use context path and keys from "x-translation" extension annotations
		// to translate supported annotations and finally remove "x-translation" extensions
		Info resourceInfo = openAPI.getInfo();
		ContextInformation resourceContext = getContextInformation(openAPI.getExtensions());
		removeTranslationAnnotations(openAPI.getExtensions());
		TranslateProperties(Constants.TRANSLATABLE_INFO_PROPERTIES, resourceContext, resourceInfo);
		
		for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet())
		{
			PathItem pathItem = pathEntry.getValue();
			ContextInformation pathContext = getContextInformation(pathItem.getExtensions(), resourceContext);
			removeTranslationAnnotations(pathItem.getExtensions());
			TranslateProperties(Constants.TRANSLATABLE_PATH_ITEM_PROPERTIES, pathContext, pathItem);
			
			for (Operation operation : pathItem.readOperations()) {
				ContextInformation operationContext = getContextInformation(operation.getExtensions(), pathContext);
				removeTranslationAnnotations(operation.getExtensions());
				TranslateProperties(Constants.TRANSLATABLE_OPERATION_PROPERTIES, operationContext, operation);
				
				addApiErrorResponses(operation);
				removeApiErrorsAnnotations(operation.getExtensions());

				for (Map.Entry<String, ApiResponse> responseEntry : operation.getResponses().entrySet()) {
					ApiResponse response = responseEntry.getValue();
					ContextInformation responseContext = getContextInformation(response.getExtensions(), operationContext);
					removeTranslationAnnotations(response.getExtensions());
					TranslateProperties(Constants.TRANSLATABLE_API_RESPONSE_PROPERTIES, responseContext, response);
				}
			}
		}
	}

	private void addApiErrorResponses(Operation operation) {
		List<ApiError> apiErrors = getApiErrors(operation.getExtensions());
		if(apiErrors != null) {
			for(ApiError apiError : apiErrors) {
				String statusCode = Integer.toString(apiError.getStatus());
				ApiResponse apiResponse = operation.getResponses().get(statusCode);
				if(apiResponse == null) {
					Schema errorMessageSchema = ModelConverters.getInstance().readAllAsResolvedSchema(ApiErrorMessage.class).schema;
					MediaType mediaType = new MediaType().schema(errorMessageSchema);
					Content content = new Content().addMediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON, mediaType);
					apiResponse = new ApiResponse().content(content);
					operation.getResponses().addApiResponse(statusCode, apiResponse);
				}
				
				int apiErrorCode = apiError.getCode();
				ApiErrorMessage apiErrorMessage = new ApiErrorMessage(apiErrorCode, this.apiErrorFactory.getErrorMessage(apiError));
				Example example = new Example().value(apiErrorMessage);
				
				// XXX: addExamples(..) is not working in Swagger 2.0.4. This bug is referenced in https://github.com/swagger-api/swagger-ui/issues/2651
				// Replace the call to .setExample(..) by .addExamples(..) when the bug is fixed.
				apiResponse.getContent().get(javax.ws.rs.core.MediaType.APPLICATION_JSON).setExample(example);
				//apiResponse.getContent().get(javax.ws.rs.core.MediaType.APPLICATION_JSON).addExamples(Integer.toString(apiErrorCode), example);
			}
		}
	}

	private <T> void TranslateProperties(List<TranslatableProperty<T>> translatableProperties, ContextInformation context, T item) {
		if(context.keys != null) {
			Map<String, String> keys = context.keys;
			for(TranslatableProperty<T> prop : translatableProperties) {
				String key = keys.get(prop.keyName());
				if(key != null) {
					String originalValue = prop.getValue(item);
					// XXX: use browser locale instead default?
					String translation = translator.translate(context.path, key, originalValue);
					prop.setValue(item, translation);
				}
			}
		}
	}

	private List<ApiError> getApiErrors(Map<String, Object> extensions) {
		if(extensions == null)
			return null;

		List<String> apiErrorStrings = new ArrayList();
		try {
			ArrayNode apiErrorsNode = (ArrayNode)extensions.get("x-" + Constants.API_ERRORS_EXTENSION_NAME);
			if(apiErrorsNode == null)
				return null;

			for(int i = 0; i < apiErrorsNode.size(); i++) {
				String errorString = apiErrorsNode.get(i).asText();
				apiErrorStrings.add(errorString);
			}
		} catch(Exception e) {
			// TODO: error logging
			return null;
		}
		
		List<ApiError> result = new ArrayList<>();
		for(String apiErrorString : apiErrorStrings) {
			ApiError apiError = null;
			try {
				apiError = ApiError.valueOf(apiErrorString);
			} catch(IllegalArgumentException e) {
				try {
					int errorCodeInt = Integer.parseInt(apiErrorString);
					apiError =  ApiError.fromCode(errorCodeInt);
				} catch (NumberFormatException ex) {
					return null;
				}
			}
			
			if(apiError == null)
				return null;
			
			result.add(apiError);
		}
		
		return result;
	}

	private ContextInformation getContextInformation(Map<String, Object> extensions) {
		return getContextInformation(extensions, null);
	}
	
	private ContextInformation getContextInformation(Map<String, Object> extensions, ContextInformation base) {
		if(extensions != null) {
			Map<String, Object> translationDefinitions = (Map<String, Object>)extensions.get("x-" + Constants.TRANSLATION_EXTENSION_NAME);
			if(translationDefinitions != null) {
				ContextInformation result = new ContextInformation();
				result.path = combinePaths(base, (String)translationDefinitions.get(Constants.TRANSLATION_PATH_EXTENSION_NAME));
				result.keys = getTranslationKeys(translationDefinitions);
				return result;
			}
		}
		
		if(base != null) {
			ContextInformation result = new ContextInformation();
			result.path = base.path;
			return result;
		}
		
		return null;
	}

	private void removeApiErrorsAnnotations(Map<String, Object> extensions) {
		String extensionName = Constants.API_ERRORS_EXTENSION_NAME;
		removeExtension(extensions, extensionName);
	}
	
	private void removeTranslationAnnotations(Map<String, Object> extensions) {
		String extensionName = Constants.TRANSLATION_EXTENSION_NAME;
		removeExtension(extensions, extensionName);
	}
	
	private void removeExtension(Map<String, Object> extensions, String extensionName) {
		if(extensions == null)
			return;
		
		extensions.remove("x-" + extensionName);
	}
	
	private Map<String, String> getTranslationKeys(Map<String, Object> translationDefinitions) {
		Map<String, String> result = new HashMap<>();
		
		for(TranslatableProperty prop : Constants.TRANSLATABLE_INFO_PROPERTIES) {
			String key = (String)translationDefinitions.get(prop.keyName());
			if(key != null)
				result.put(prop.keyName(), key);
		}
		
		return result;
	}
	
	private String combinePaths(ContextInformation base, String path) {
		String basePath = (base != null) ? base.path : null;
		return ContextPaths.combinePaths(basePath, path);
	}
}
