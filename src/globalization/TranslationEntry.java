package globalization;

import java.util.Locale;

public class TranslationEntry {
	private Locale locale;
	private String path;
	private String template;
	
	public TranslationEntry(Locale locale, String path, String template) {
		this.locale = locale;
		this.path = path;
		this.template = template;
	}
	
	public Locale locale() {
		return this.locale;
	}
	
	public String path() {
		return this.path;
	}
	
	public String template() {
		return this.template;
	}

	@Override
	public String toString() {
		return String.format("{locale: '%s', path: '%s', template: '%s'}", this.locale, this.path, this.template);
	}
}
