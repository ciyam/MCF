package org.qora.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.eclipse.persistence.oxm.annotations.XmlVariableNode;

public class StringLongMapXmlAdapter extends XmlAdapter<StringLongMapXmlAdapter.StringLongMap, Map<String, Long>> {

	public static class StringLongMap {
		@XmlVariableNode("key")
		List<MapEntry> entries = new ArrayList<MapEntry>();
	}

	public static class MapEntry {
		@XmlTransient
		public String key;

		@XmlValue
		public Long value;
	}

	@Override
	public Map<String, Long> unmarshal(StringLongMap stringLongMap) throws Exception {
		Map<String, Long> map = new HashMap<>(stringLongMap.entries.size());

		for (MapEntry entry : stringLongMap.entries)
			map.put(entry.key, entry.value);

		return map;
	}

	@Override
	public StringLongMap marshal(Map<String, Long> map) throws Exception {
		StringLongMap output = new StringLongMap();

		for (Entry<String, Long> entry : map.entrySet()) {
			MapEntry mapEntry = new MapEntry();
			mapEntry.key = entry.getKey();
			mapEntry.value = entry.getValue();
			output.entries.add(mapEntry);
		}

		return output;
	}
}
