package globalization;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import settings.Settings;

/** Providing multi-language BIP39 word lists, downloaded from https://github.com/bitcoin/bips/tree/master/bip-0039 */
public class BIP39WordList {

	private static BIP39WordList instance;

	private static Map<String, List<String>> wordListsByLang;

	private BIP39WordList() {
		wordListsByLang = new HashMap<>();

		String path = Settings.getInstance().translationsPath();
		File dir = new File(path);
		File[] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("BIP39.");
			}
		});

		try {
			for (File file : files) {
				String lang = file.getName().substring(6, 8);
				List<String> words = Files.readAllLines(file.toPath());
				wordListsByLang.put(lang, words);
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to read BIP39 word list", e);
		}
	}

	public static synchronized BIP39WordList getInstance() {
		if (instance == null)
			instance = new BIP39WordList();

		return instance;
	}

	public List<String> getByLang(String lang) {
		return Collections.unmodifiableList(wordListsByLang.get(lang));
	}

}
