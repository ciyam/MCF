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
import org.junit.Assert;
import static org.junit.Assert.*;
import static test.utils.AssertExtensions.*;

import org.junit.Test;
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
	public void TestTranslationXmlReader() throws XMLStreamException {
		String xml = 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<localization>\n" +
			"	<context locale=\"en-GB\">\n" +
			"		<context path=\"path1\">\n" +
			"			<context path=\"path2/path3\">\n" +
			"				<translation key=\"key1\" template=\"1\" />\n" +
			"				<translation key=\"key2\" template=\"2\" />\n" +
			"			</context>\n" +
			"		</context>\n" +
			"	</context>\n" +
			"</localization>\n";
		
		List<TranslationEntry> expected = new ArrayList<TranslationEntry>();
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/path2/path3/key1", "1"));
		expected.add(new TranslationEntry(Locale.forLanguageTag("en-GB"), "/path1/path2/path3/key2", "2"));
		
		InputStream is = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
		TranslationXmlStreamReader reader = new TranslationXmlStreamReader();
		Iterable<TranslationEntry> actual = reader.ReadFrom(is);		
		
		assertSetEquals(expected, actual, new TranslationEntryEqualityComparer());
	}
	
}
