
package api;

import globalization.ContextPaths;
import globalization.Translator;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationPostProcessor implements ReaderListener {

	private class ContextInformation {
		public String path;
		public Map<String, String> keys;
	}

	private final Translator translator;
	
	public AnnotationPostProcessor() {
		this(Translator.getInstance());
	}
	
	public AnnotationPostProcessor(Translator translator) {
		this.translator = translator;
	}
	
	@Override
	public void beforeScan(Reader reader, OpenAPI openAPI) {}

	@Override
	public void afterScan(Reader reader, OpenAPI openAPI) {
		// use context path and keys from "x-translation" extension annotations
		// to translate supported annotations and finally remove "x-translation" extensions
		Info resourceInfo = openAPI.getInfo();
		ContextInformation resourceContext = getContextInformation(openAPI.getExtensions());
		removeTranslationAnnotations(openAPI.getExtensions());
		TranslateProperty(Constants.TRANSLATABLE_INFO_PROPERTIES, resourceContext, resourceInfo);
		
		for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet())
		{
			PathItem pathItem = pathEntry.getValue();
			ContextInformation pathContext = getContextInformation(pathItem.getExtensions(), resourceContext);
			removeTranslationAnnotations(pathItem.getExtensions());
			TranslateProperty(Constants.TRANSLATABLE_PATH_ITEM_PROPERTIES, pathContext, pathItem);
			
			for (Operation operation : pathItem.readOperations()) {
				ContextInformation operationContext = getContextInformation(operation.getExtensions(), pathContext);
				removeTranslationAnnotations(operation.getExtensions());
				TranslateProperty(Constants.TRANSLATABLE_OPERATION_PROPERTIES, operationContext, operation);
				
				for (Map.Entry<String, ApiResponse> responseEntry : operation.getResponses().entrySet()) {
					ApiResponse response = responseEntry.getValue();
					ContextInformation responseContext = getContextInformation(response.getExtensions(), operationContext);
					removeTranslationAnnotations(response.getExtensions());
					TranslateProperty(Constants.TRANSLATABLE_API_RESPONSE_PROPERTIES, responseContext, response);
				}
			}
		}
	}

	private <T> void TranslateProperty(List<TranslatableProperty<T>> translatableProperties, ContextInformation context, T item) {
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

	private void removeTranslationAnnotations(Map<String, Object> extensions) {
		if(extensions == null)
			return;
		
		extensions.remove("x-" + Constants.TRANSLATION_EXTENSION_NAME);
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
