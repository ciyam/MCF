package globalization;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;

public class Translator {

	private Map<String, Object> createMap(Map.Entry<String, Object>[] entries) {
		HashMap<String, Object> map = new HashMap<>();
		for (AbstractMap.Entry<String, Object> entry : entries) {
			map.put(entry.getKey(), entry.getValue());
		}
		return map;
	}

	//XXX: replace singleton pattern by dependency injection?
	private static Translator instance;

	public static Translator getInstance() {
		if (instance == null) {
			instance = new Translator();
		}

		return instance;
	}

	public String translate(Locale locale, String templateKey, AbstractMap.Entry<String, Object>... templateValues) {
		Map<String, Object> map = createMap(templateValues);
		return translate(locale, templateKey, map);
	}

	public String translate(Locale locale, String templateKey, Map<String, Object> templateValues) {
		return translate(locale, templateKey, null, templateValues);
	}

	public String translate(Locale locale, String templateKey, String defaultTemplate, AbstractMap.Entry<String, Object>... templateValues) {
		Map<String, Object> map = createMap(templateValues);
		return translate(locale, templateKey, defaultTemplate, map);
	}

	public String translate(Locale locale, String templateKey, String defaultTemplate, Map<String, Object> templateValues) {
		String template = defaultTemplate; // TODO: get template for the given locale if available

		StringSubstitutor sub = new StringSubstitutor(templateValues);
		String result = sub.replace(template);

		return result;
	}
}
