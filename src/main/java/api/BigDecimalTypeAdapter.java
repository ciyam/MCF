package api;

import java.math.BigDecimal;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class BigDecimalTypeAdapter extends XmlAdapter<String, BigDecimal> {

	@Override
	public BigDecimal unmarshal(String input) throws Exception {
		if (input == null)
			return null;

		return new BigDecimal(input).setScale(8);
	}

	@Override
	public String marshal(BigDecimal output) throws Exception {
		if (output == null)
			return null;

		return output.toPlainString();
	}

}
