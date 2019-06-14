package org.qora.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.qora.utils.ByteArray;

public class ByteArrayTests {

	private static List<byte[]> testValues;

	@Before
	public void createTestValues() {
		Random random = new Random();

		testValues = new ArrayList<>();
		for (int i = 0; i < 5; ++i) {
			byte[] testValue = new byte[32];
			random.nextBytes(testValue);
			testValues.add(testValue);
		}
	}

	public void fillMap(Map<ByteArray, String> map) {
		for (byte[] testValue : testValues)
			map.put(new ByteArray(testValue), String.valueOf(map.size()));
	}

	@Test
	public void testByteArray() {
		// Create two objects, which will have different references, but same content.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = new ByteArray(testValue);
		ByteArray ba2 = new ByteArray(testValue);

		// Confirm JVM-assigned references are different
		assertNotSame(ba1, ba2);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(ba2));
		assertEquals("ba1 not equal to ba2", ba1, ba2);

		// Confirm "hashCode" results match
		assertEquals("hashCodes do not match", ba1.hashCode(), ba2.hashCode());
	}

	@Test
	public void testByteArrayMap() {
		Map<ByteArray, String> testMap = new HashMap<>();
		fillMap(testMap);

		// Create new ByteArray object with an existing value.
		ByteArray ba = new ByteArray(testValues.get(3));

		// Confirm object can be found in map
		assertTrue("ByteArray not found in map", testMap.containsKey(ba));
	}

}
