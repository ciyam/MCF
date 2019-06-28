package org.qora.globalization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum Translator {
	INSTANCE;

	private final Logger LOGGER = LogManager.getLogger(Translator.class);
	private final String DEFAULT_LANG = Locale.getDefault().getLanguage();

	private final Map<String, ResourceBundle> resourceBundles = new HashMap<>();

	private synchronized ResourceBundle getOrLoadResourceBundle(String className, String lang) {
		final String bundleKey = className + ":" + lang;

		ResourceBundle resourceBundle = resourceBundles.get(bundleKey);
		if (resourceBundle != null)
			return resourceBundle;

		try {
			resourceBundle = ResourceBundle.getBundle("i18n." + className, Locale.forLanguageTag(lang));
		} catch (MissingResourceException e) {
			LOGGER.warn("Can't locate '" + lang + "' translation resource bundle for " + className, e);
			return null;
		}

		resourceBundles.put(bundleKey, resourceBundle);

		return resourceBundle;
	}

	public String translate(final String className, final String key) {
		return this.translate(className, DEFAULT_LANG, key);
	}

	public String translate(final String className, final String lang, final String key, final Object... args) {
		ResourceBundle resourceBundle = getOrLoadResourceBundle(className, lang);

		if (resourceBundle == null || !resourceBundle.containsKey(key))
			return "!!" + lang + ":" + className + "." + key + "!!";

		String template = resourceBundle.getString(key);
		try {
			return String.format(template, args);
		} catch (MissingFormatArgumentException e) {
			return template;
		}
	}

}
