// This file (data/package-info.java) is used as a template!

@XmlJavaTypeAdapters({
	@XmlJavaTypeAdapter(
		type = byte[].class,
		value = api.Base58TypeAdapter.class
	), @XmlJavaTypeAdapter(
		type = java.math.BigDecimal.class,
		value = api.BigDecimalTypeAdapter.class
	)
})
package data;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
