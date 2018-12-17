package utils;

import java.util.ArrayList;
import java.util.List;

import globalization.BIP39WordList;

public class BIP39 {

	private static final int BITS_PER_WORD = 11;

	/** Convert BIP39 mnemonic to binary 'entropy' */
	public static byte[] decode(String[] phraseWords, String lang) {
		if (lang == null)
			lang = "en";

		List<String> wordList = BIP39WordList.getInstance().getByLang(lang);
		if (wordList == null)
			throw new IllegalStateException("BIP39 word list for lang '" + lang + "' unavailable");

		byte[] entropy = new byte[(phraseWords.length * BITS_PER_WORD + 7) / 8];
		int byteIndex = 0;
		int bitShift = 3;

		for (int i = 0; i < phraseWords.length; ++i) {
			int wordListIndex = wordList.indexOf(phraseWords[i]);
			if (wordListIndex == -1)
				// Word not found
				return null;

			entropy[byteIndex++] |= (byte) (wordListIndex >> bitShift);

			bitShift = 8 - bitShift;
			if (bitShift >= 0) {
				// Leftover fits inside one byte
				entropy[byteIndex] |= (byte) ((wordListIndex << bitShift));
				bitShift = BITS_PER_WORD - bitShift;
			} else {
				// Leftover spread over next two bytes
				bitShift = 0 - bitShift;
				entropy[byteIndex++] |= (byte) (wordListIndex >> bitShift);

				entropy[byteIndex] |= (byte) ((wordListIndex << (8 - bitShift)));
				bitShift = bitShift + BITS_PER_WORD - 8;
			}
		}

		return entropy;
	}

	/** Convert binary entropy to BIP39 mnemonic */
	public static String encode(byte[] entropy, String lang) {
		if (lang == null)
			lang = "en";

		List<String> wordList = BIP39WordList.getInstance().getByLang(lang);
		if (wordList == null)
			throw new IllegalStateException("BIP39 word list for lang '" + lang + "' unavailable");

		List<String> phraseWords = new ArrayList<>();

		int bitMask = 128; // MSB first
		int byteIndex = 0;
		while (true) {
			int wordListIndex = 0;
			for (int bitCount = 0; bitCount < BITS_PER_WORD; ++bitCount) {
				wordListIndex <<= 1;

				if ((entropy[byteIndex] & bitMask) != 0)
					++wordListIndex;

				bitMask >>= 1;
				if (bitMask == 0) {
					bitMask = 128;
					++byteIndex;

					if (byteIndex >= entropy.length)
						return String.join(" ", phraseWords);
				}
			}

			phraseWords.add(wordList.get(wordListIndex));
		}
	}

}
