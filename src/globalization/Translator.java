package globalization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.text.StringSubstitutor;

import settings.Settings;

public class Translator {

	Map<Locale, Map<String, String>> translations = new HashMap<Locale, Map<String, String>>();

	//XXX: replace singleton pattern by dependency injection?
	private static Translator instance;

	private Translator() {
		InitializeTranslations();
	}
	
	public static Translator getInstance() {
		if (instance == null) {
			instance = new Translator();
		}

		return instance;
	}
	
	private Settings settings() {
		return Settings.getInstance();
	}
	
	private void InitializeTranslations() {
		String path = this.settings().translationsPath();
		File dir = new File(path);
		File [] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".xml");
			}
		});

		Map<Locale, Map<String, String>> translations = new HashMap<>();
		TranslationXmlStreamReader translationReader = new TranslationXmlStreamReader();
		for (File file : files) {
			Iterable<TranslationEntry> entries = null;
			try {
				InputStream stream = new FileInputStream(file);
				entries = translationReader.ReadFrom(stream);
			} catch (FileNotFoundException ex) {
				Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, String.format("Translation file not found: %s", file), ex);
			} catch (XMLStreamException ex) {
				Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, String.format("Error in translation file: %s", file), ex);
			}
			
			for(TranslationEntry entry : entries) {
				Map<String, String> localTranslations = translations.get(entry.locale());
				if(localTranslations == null) {
					localTranslations = new HashMap<>();
					translations.put(entry.locale(), localTranslations);
				}
				
				if(localTranslations.containsKey(entry.path())) {
					Logger.getLogger(Translator.class.getName()).log(Level.SEVERE, String.format("Duplicate entry for locale '%s' and path '%s' in translation file '%s'. Falling back to default translations.", entry.locale(), entry.path(), file));
					return;
				}
			}
		}

		// everything is fine, so we store all read translations
		this.translations = translations;
	}

	private Map<String, Object> createMap(Map.Entry<String, Object>[] entries) {
		HashMap<String, Object> map = new HashMap<>();
		for (AbstractMap.Entry<String, Object> entry : entries) {
			map.put(entry.getKey(), entry.getValue());
		}
		return map;
	}

	public String translate(Locale locale, String contextPath, String templateKey, AbstractMap.Entry<String, Object>... templateValues) {
		Map<String, Object> map = createMap(templateValues);
		return translate(locale, contextPath, templateKey, map);
	}

	public String translate(Locale locale, String contextPath, String templateKey, Map<String, Object> templateValues) {
		return translate(locale, contextPath, templateKey, null, templateValues);
	}

	public String translate(Locale locale, String contextPath, String templateKey, String defaultTemplate, AbstractMap.Entry<String, Object>... templateValues) {
		Map<String, Object> map = createMap(templateValues);
		return translate(locale, contextPath, templateKey, defaultTemplate, map);
	}

	public String translate(Locale locale, String contextPath, String templateKey, String defaultTemplate, Map<String, Object> templateValues) {
		// look for requested language
		String template = getTemplateFromNearestPath(locale, contextPath, templateKey);
		
		if(template == null) {
			// scan default languages
			for(String language : this.settings().translationsDefaultLocales()) {
				Locale defaultLocale = Locale.forLanguageTag(language);
				template = getTemplateFromNearestPath(defaultLocale, contextPath, templateKey);
				if(template != null)
					break;
			}
		}
		
		if(template == null)
			template = defaultTemplate; // fallback template

		StringSubstitutor sub = new StringSubstitutor(templateValues);
		String result = sub.replace(template);

		return result;
	}
	
	private String getTemplateFromNearestPath(Locale locale, String contextPath, String templateKey) {
		Map<String, String> localTranslations = this.translations.get(locale);
		if(localTranslations == null)
			return null;

		String template = null;
		while(true) {
			String path = ContextPaths.combinePaths(contextPath, templateKey);
			template = localTranslations.get(path);
			if(template != null)
				break; // found template
			if(ContextPaths.isRoot(contextPath))
				break; // nothing found
			contextPath = ContextPaths.getParent(contextPath);
		}
		
		return template;
	}
}
