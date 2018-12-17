package api;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.bitcoinj.core.Base58;

public class Base58TypeAdapter extends XmlAdapter<String, byte[]> {

	@Override
	public byte[] unmarshal(String input) throws Exception {
		if (input == null)
			return null;

		return Base58.decode(input);
	}

	@Override
	public String marshal(byte[] input) throws Exception {
		if (input == null)
			return null;

		return Base58.encode(input);
	}

}
