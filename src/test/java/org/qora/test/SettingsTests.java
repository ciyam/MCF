package org.qora.test;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qora.block.BlockChain;
import org.qora.data.transaction.TransactionData;
import org.qora.settings.Settings;

public class SettingsTests {

	public static void main(String[] args) throws JAXBException, IOException {
		// JAXBContext jc = JAXBContext.newInstance(SettingsData.class);
		JAXBContext jc = JAXBContextFactory.createContext(new Class[] {Settings.class, BlockChain.class, TransactionData.class}, null);

		// Create the Unmarshaller Object using the JaxB Context
		Unmarshaller unmarshaller = jc.createUnmarshaller();

		// Set the Unmarshaller media type to JSON or XML
		unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

		// Set it to true if you need to include the JSON root element in the JSON input
		unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

		Settings settings = null;

		// Create the StreamSource by creating Reader to the JSON input
		try (Reader settingsReader = new FileReader("settings.json")) {
			StreamSource json = new StreamSource(settingsReader);

			// Getting the SettingsData pojo  from the json
			settings = unmarshaller.unmarshal(json, Settings.class).getValue();

			System.out.println("API settings:");
			System.out.println(String.format("Enabled: %s, port: %d, restricted: %s, whitelist: %s", yn(settings.isApiEnabled()), settings.getApiPort(),
					yn(settings.isApiRestricted()), String.join(", ", settings.getApiWhitelist())));
		}

		String blockchainConfig = settings.getBlockchainConfig();
		if (blockchainConfig != null)
			try (Reader settingsReader = new FileReader(blockchainConfig)) {
				StreamSource json = new StreamSource(settingsReader);

				// Getting the BlockChainData pojo from the JSON
				BlockChain blockchain = unmarshaller.unmarshal(json, BlockChain.class).getValue();

				System.out.println("BlockChain settings:");
				System.out.println(String.format("TestNet: %s", yn(blockchain.isTestNet())));
			}
	}

	private static String yn(boolean flag) {
		return flag ? "yes" : "no";
	}

}
