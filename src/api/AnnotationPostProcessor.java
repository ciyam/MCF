
package api;

import globalization.ContextPaths;
import globalization.Translator;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import static java.util.Arrays.asList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnnotationPostProcessor implements ReaderListener {

	private interface TranslatableProperty<T> {
		public String keyName();
		public void setValue(T item, String translation);
		public String getValue(T item);
	}
	
	private class ContextInformation {
		public String path;
		public Map<String, String> keys;
	}

	private static final String TRANSLATION_EXTENTION_NAME = "x-translation";
	
	private static final List<TranslatableProperty<Info>> translatableInfoProperties = asList(
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return "description.key"; }
			@Override public void setValue(Info item, String translation) { item.setDescription(translation); }
			@Override public String getValue(Info item) { return item.getDescription(); }
		},
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return "title.key"; }
			@Override public void setValue(Info item, String translation) { item.setTitle(translation); }
			@Override public String getValue(Info item) { return item.getTitle(); }
		},
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return "termsOfService.key"; }
			@Override public void setValue(Info item, String translation) { item.setTermsOfService(translation); }
			@Override public String getValue(Info item) { return item.getTermsOfService(); }
		}
	);
	
	private static final List<TranslatableProperty<PathItem>> translatablePathItemProperties = asList(
		new TranslatableProperty<PathItem>() {
			@Override public String keyName() { return "description.key"; }
			@Override public void setValue(PathItem item, String translation) { item.setDescription(translation); }
			@Override public String getValue(PathItem item) { return item.getDescription(); }
		},
		new TranslatableProperty<PathItem>() {
			@Override public String keyName() { return "summary.key"; }
			@Override public void setValue(PathItem item, String translation) { item.setSummary(translation); }
			@Override public String getValue(PathItem item) { return item.getSummary(); }
		}
	);
	
	private static final List<TranslatableProperty<Operation>> translatableOperationProperties = asList(
		new TranslatableProperty<Operation>() {
			@Override public String keyName() { return "description.key"; }
			@Override public void setValue(Operation item, String translation) { item.setDescription(translation); }
			@Override public String getValue(Operation item) { return item.getDescription(); }
		},
		new TranslatableProperty<Operation>() {
			@Override public String keyName() { return "summary.key"; }
			@Override public void setValue(Operation item, String translation) { item.setSummary(translation); }
			@Override public String getValue(Operation item) { return item.getSummary(); }
		}
	);
	
	private static final List<TranslatableProperty<ApiResponse>> translatableApiResponseProperties = asList(
		new TranslatableProperty<ApiResponse>() {
			@Override public String keyName() { return "description.key"; }
			@Override public void setValue(ApiResponse item, String translation) { item.setDescription(translation); }
			@Override public String getValue(ApiResponse item) { return item.getDescription(); }
		}
	);
	
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
		TranslateProperty(translatableInfoProperties, resourceContext, resourceInfo);
		
		for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet())
		{
			PathItem pathItem = pathEntry.getValue();
			ContextInformation pathContext = getContextInformation(pathItem.getExtensions(), resourceContext);
			removeTranslationAnnotations(pathItem.getExtensions());
			TranslateProperty(translatablePathItemProperties, pathContext, pathItem);
			
			for (Operation operation : pathItem.readOperations()) {
				ContextInformation operationContext = getContextInformation(operation.getExtensions(), pathContext);
				removeTranslationAnnotations(operation.getExtensions());
				TranslateProperty(translatableOperationProperties, operationContext, operation);
				
				for (Map.Entry<String, ApiResponse> responseEntry : operation.getResponses().entrySet()) {
					ApiResponse response = responseEntry.getValue();
					ContextInformation responseContext = getContextInformation(response.getExtensions(), operationContext);
					removeTranslationAnnotations(response.getExtensions());
					TranslateProperty(translatableApiResponseProperties, responseContext, response);
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
					String translation = translator.translate(Locale.ENGLISH, context.path, key, originalValue);
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
			Map<String, Object> translationDefinitions = (Map<String, Object>)extensions.get(TRANSLATION_EXTENTION_NAME);
			if(translationDefinitions != null) {
				ContextInformation result = new ContextInformation();
				result.path = getAbsolutePath(base, (String)translationDefinitions.get("path"));
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
		
		extensions.remove(TRANSLATION_EXTENTION_NAME);
	}
	
	private Map<String, String> getTranslationKeys(Map<String, Object> translationDefinitions) {
		Map<String, String> result = new HashMap<>();
		
		for(TranslatableProperty prop : translatableInfoProperties) {
			String key = (String)translationDefinitions.get(prop.keyName());
			if(key != null)
				result.put(prop.keyName(), key);
		}
		
		return result;
	}
	
	private String getAbsolutePath(ContextInformation base, String path) {
		String result = (base != null) ? base.path : "/";
		path = (path != null) ? path : "";
		result = ContextPaths.combinePaths(result, path);
		return result;
	}
}
