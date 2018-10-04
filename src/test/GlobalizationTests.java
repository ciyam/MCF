package test;

import globalization.TranslationEntry;
import globalization.TranslationXmlStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

import static test.utils.AssertExtensions.*;
import test.utils.EqualityComparer;

public class GlobalizationTests {
	
	private class TranslationEntryEqualityComparer implements EqualityComparer<TranslationEntry> {

		@Override
		public boolean equals(TranslationEntry first, TranslationEntry second) {
			if(first == null && second == null)
				return true;
			if(first == null && second != null || first != null && second == null)
				return false;
			
			if(!first.locale().equals(second.locale()))
				return false;
			if(!first.path().equals(second.path()))
				return false;
			if(!first.template().equals(second.template()))
				return false;
			
			return true;
		}

		@Override
		public int hashCode(TranslationEntry item) {
			int hash = 17; 
			final int multiplier = 59; 

			hash = hash * multiplier + item.locale().hashCode(); 
			hash = hash * multiplier + item.path().hashCode(); 
			hash = hash * multiplier + item.template().hashCode(); 

			return hash;
		}
	
	}

	@Test
	public void TestTranslationXmlReaderContextPaths() throws XMLStreamException {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<context locale=\"en-GB\">\n" +
			"		<context path=\"path1/\">\n" +
			"			<translation key=\"key1\" template=\"1\" />\n" +
			"			<context path=\"./path2//path3\">\n" +
			"				<translation key=\"key2\" template=\"2\" />\n" +
			"			</context>\n" +
			"			<context path=\"/path4\">\n" +
			"				<translation key=\"key3\" template=\"3\" />\n" +
			"			</context>\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		List<TranslationEntry> expected = new ArrayList<TranslationEntry>();
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/key1", "1"));
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/path2/path3/key2", "2"));
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/path4/key3", "3"));
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		Iterable<TranslationEntry> actual = reader.ReadFrom(is);		
		
		for(TranslationEntry i:expected)System.out.println(i);for(TranslationEntry i:actual)System.out.println(i);
		assertItemsEqual(expected, actual, new TranslationEntryEqualityComparer());
	}
	
	@Test
	public void TestTranslationXmlReaderLocales() throws XMLStreamException {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<translation key=\"key1\" template=\"1\" />\n" +
			"	<context locale=\"en-GB\" path=\"path1\">\n" +
			"		<translation key=\"key2\" template=\"2\" />\n" +
			"		<context locale=\"de-DE\" path=\"path2/\">\n" +
			"			<translation key=\"key3\" template=\"3\" />\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		List<TranslationEntry> expected = new ArrayList<TranslationEntry>();
		expected.add(new TranslationEntry(Locale.forLanguageTag("default"), "/key1", "1"));
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/key2", "2"));
		expected.add(new TranslationEntry(Locale.forLanguageTag("de-DE"), "/path1/path2/key3", "3"));
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		Iterable<TranslationEntry> actual = reader.ReadFrom(is);		

		assertItemsEqual(expected, actual, new TranslationEntryEqualityComparer());
	}
	
	@Test
	public void TestTranslationXmlReader_BadPath() {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<context locale=\"en-GB\">\n" +
			"		<context path=\"path1\">\n" +
			"			<context path=\"../path2\">\n" +
			"				<translation key=\"key1\" template=\"1\" />\n" +
			"			</context>\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		
		assertThrows(XMLStreamException.class, () -> reader.ReadFrom(is));
	}
	
	@Test
	public void TestTranslationXmlReader_BadKey1() {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<context locale=\"en-GB\">\n" +
			"		<context path=\"path1\">\n" +
			"			<context path=\"path2\">\n" +
			"				<translation key=\"path3/key1\" template=\"1\" />\n" +
			"			</context>\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		
		assertThrows(XMLStreamException.class, () -> reader.ReadFrom(is));
	}
	
	@Test
	public void TestTranslationXmlReader_BadKey2() {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<context locale=\"en-GB\">\n" +
			"		<context path=\"path1\">\n" +
			"			<context path=\"path2\">\n" +
			"				<translation key=\"..\" template=\"1\" />\n" +
			"			</context>\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		
		assertThrows(XMLStreamException.class, () -> reader.ReadFrom(is));
	}
}
