// This file (data/package-info.java) is used as a template!

@XmlJavaTypeAdapters({
	@XmlJavaTypeAdapter(
		type = byte[].class,
		value = org.qora.api.Base58TypeAdapter.class
	), @XmlJavaTypeAdapter(
		type = java.math.BigDecimal.class,
		value = org.qora.api.BigDecimalTypeAdapter.class
	)
})
package org.qora.data;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
