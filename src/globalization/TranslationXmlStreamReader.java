/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package globalization;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.xml.namespace.QName;

import javax.xml.stream.XMLEventReader; 
import javax.xml.stream.XMLInputFactory; 
import javax.xml.stream.XMLStreamException; 
import javax.xml.stream.events.*; 
import org.apache.commons.text.StringEscapeUtils;

public class TranslationXmlStreamReader {

	private class State {
		
		public final Locale locale;
		public final String path;
		
		public State(Locale locale, String path) {
			this.locale = locale;
			this.path = path;			
		}
	}

	private static final String LOCALIZATION_TAG_NAME = "localization";

	private static final String CONTEXT_TAG_NAME = "context";
	private static final String CONTEXT_LOCALE_ATTRIBUTE_NAME = "locale";
	private static final String CONTEXT_PATH_ATTRIBUTE_NAME = "path";

	private static final String TRANSLATION_TAG_NAME = "translation";
	private static final String TRANSLATION_KEY_ATTRIBUTE_NAME = "key";
	private static final String TRANSLATION_TEMPLATE_ATTRIBUTE_NAME = "template";
	
	public Iterable<TranslationEntry> ReadFrom(InputStream stream) throws XMLStreamException {
		XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLEventReader eventReader = inputFactory.createXMLEventReader(stream);
		
		XMLEvent element = eventReader.nextEvent();
		if(!element.isStartDocument())
			throw new javax.xml.stream.XMLStreamException("XML declaration <?xml ... ?> must be first in the document");
		
		State state = new State(Locale.forLanguageTag("default"), "/");
		
		List<TranslationEntry> result = new ArrayList<TranslationEntry>();
		if (eventReader.hasNext()) 
        { 
			XMLEvent event = eventReader.nextTag(); 
            if (isStartElement(event, LOCALIZATION_TAG_NAME)) 
            { 
				processLocalization(eventReader, (StartElement)event, state, result);
			} else {
				throw new javax.xml.stream.XMLStreamException("Unexpected element: " + event.toString());
			}
		}

		while (eventReader.hasNext()) 
        { 
			XMLEvent event = eventReader.nextEvent(); 
			switch(event.getEventType()) {
				case XMLEvent.COMMENT:
					break;
				case XMLEvent.CHARACTERS:
					if(!event.asCharacters().isIgnorableWhiteSpace())
						throw new javax.xml.stream.XMLStreamException("Unexpected content after end of root element: " + event.toString());
					break;
				case XMLEvent.END_DOCUMENT:
					return result;
				default:
					throw new javax.xml.stream.XMLStreamException("Unexpected content after end of root element: " + event.toString());
			}
		}
		
		throw new javax.xml.stream.XMLStreamException("End of document not found");
	}
	
	private void processLocalization(XMLEventReader eventReader, StartElement element, State state, List<TranslationEntry> result) throws XMLStreamException {
		assureStartElement(element, LOCALIZATION_TAG_NAME);
		
		Iterator<Attribute> attributes = element.getAttributes(); 
		while (attributes.hasNext()) 
		{ 
			Attribute attribute = attributes.next(); 
			QName name = attribute.getName();
			throw new javax.xml.stream.XMLStreamException("Unexpected attribute: " + name);
		} 

		XMLEvent event;
		while(!(event = eventReader.nextTag()).isEndElement()) {
			if(event.isStartElement()) {
				StartElement childElement = (StartElement)event;
				switch(childElement.getName().toString()) {
					case CONTEXT_TAG_NAME:
						processContext(eventReader, childElement, state, result);
						break;
					case TRANSLATION_TAG_NAME:
						processTranslation(eventReader, childElement, state, result);
						break;
					default:
						throw new javax.xml.stream.XMLStreamException("Unexpected element: " + event.toString());
				}
			} else {
				throw new javax.xml.stream.XMLStreamException("Unexpected content: " + event.toString());
			}
		}
		assureEndElement(event, LOCALIZATION_TAG_NAME);
	}

	private void processContext(XMLEventReader eventReader, StartElement element, State state, List<TranslationEntry> result) throws XMLStreamException {
		assureStartElement(element, CONTEXT_TAG_NAME);

		Locale locale = state.locale;
		String contextPath = state.path;
		
		Iterator<Attribute> attributes = element.getAttributes(); 
		while (attributes.hasNext()) 
		{ 
			Attribute attribute = attributes.next(); 
			QName name = attribute.getName(); 
			String value = attribute.getValue(); 
			switch(name.toString()) {
				case CONTEXT_LOCALE_ATTRIBUTE_NAME:
					locale = Locale.forLanguageTag(value);
					break;
				case CONTEXT_PATH_ATTRIBUTE_NAME:
					assureIsValidPathExtension(value);
					contextPath = ContextPaths.combinePaths(contextPath, value);
					break;
				default:
					throw new javax.xml.stream.XMLStreamException("Unexpected attribute: " + name);
			}
		}
		
		state = new State(locale, contextPath);
		
		XMLEvent event;
		while(!(event = eventReader.nextTag()).isEndElement()) {
			if(event.isStartElement()) {
				StartElement childElement = (StartElement)event;
				switch(childElement.getName().toString()) {
					case CONTEXT_TAG_NAME:
						processContext(eventReader, childElement, state, result);
						break;
					case TRANSLATION_TAG_NAME:
						processTranslation(eventReader, childElement, state, result);
						break;
					default:
						throw new javax.xml.stream.XMLStreamException("Unexpected element: " + event.toString());
				}
			} else {
				throw new javax.xml.stream.XMLStreamException("Unexpected content: " + event.toString());
			}
		}
		assureEndElement(event, CONTEXT_TAG_NAME);
	}


	private void processTranslation(XMLEventReader eventReader, StartElement element, State state, List<TranslationEntry> result) throws XMLStreamException {
		assureStartElement(element, TRANSLATION_TAG_NAME);

		String path = null;
		String template = null;
		
		Iterator<Attribute> attributes = element.getAttributes(); 
		while (attributes.hasNext()) 
		{ 
			Attribute attribute = attributes.next(); 
			QName name = attribute.getName(); 
			String value = attribute.getValue(); 
			switch(name.toString()) {
				case TRANSLATION_KEY_ATTRIBUTE_NAME:
					assureIsValidKey(value);
					path = ContextPaths.combinePaths(state.path, value);
					break;
				case TRANSLATION_TEMPLATE_ATTRIBUTE_NAME:
					template = unescape(value);
					break;
				default:
					throw new javax.xml.stream.XMLStreamException("Unexpected attribute: " + name);
			}
		}
		
		XMLEvent event;
		while(!(event = eventReader.nextTag()).isEndElement()) {
			if(event.isStartElement()) {
				throw new javax.xml.stream.XMLStreamException("Unexpected element: " + event.toString());
			} else if(event.isCharacters()) {
				if(template != null)
					throw new javax.xml.stream.XMLStreamException("Content must be empty if 'template' attribute is used");
				template = event.asCharacters().getData();
			}
		}
		assureEndElement(event, TRANSLATION_TAG_NAME);

		if(path == null)
			throw new javax.xml.stream.XMLStreamException("Missing attribute: " + TRANSLATION_KEY_ATTRIBUTE_NAME);

		if(template == null)
			throw new javax.xml.stream.XMLStreamException("Missing attribute: " + TRANSLATION_TEMPLATE_ATTRIBUTE_NAME);
		
		result.add(new TranslationEntry(state.locale, path, template));
	}
	
	private String unescape(String value) {
		return StringEscapeUtils.unescapeJava(value);
	}
	
	private void assureIsValidPathExtension(String value) throws XMLStreamException {
		if(ContextPaths.containsParentReference(value))
			throw new javax.xml.stream.XMLStreamException("Parent reference .. is not allowed");
	}

	private void assureIsValidKey(String value) throws XMLStreamException {
		if(!ContextPaths.isValidKey(value))
			throw new javax.xml.stream.XMLStreamException("Key is not valid");
	}

	private void assureStartElement(XMLEvent event, String name) throws XMLStreamException {
		if(!isStartElement(event, name))
			throw new javax.xml.stream.XMLStreamException("Unexpected start element: " + event.toString() + ", <" + name + "> expected");
	}
	
	private void assureEndElement(XMLEvent event, String name) throws XMLStreamException {
		if(!isEndElement(event, name))
			throw new javax.xml.stream.XMLStreamException("Unexpected end element: " + event.toString() + ", </" + name + "> expected");
	}
	
	private boolean isStartElement(XMLEvent event, String name) {
		if(!event.isStartElement())
			return false;
		StartElement element = ((StartElement)event);
		return element.getName().toString().equals(name);
	}

	private boolean isEndElement(XMLEvent event, String name) {
		if(!event.isEndElement())
			return false;
		EndElement element = ((EndElement)event);
		return element.getName().toString().equals(name);
	}
}
