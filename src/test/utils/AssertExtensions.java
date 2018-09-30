package test.utils;

import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;

public class AssertExtensions {
	
	public static <T> void assertSetEquals(Iterable<T> expected, Iterable<T> actual, EqualityComparer<T> comparer) {
		Set<EquatableWrapper<T>> expectedSet = new HashSet<EquatableWrapper<T>>();
		for(T item: expected)
			expectedSet.add(new EquatableWrapper<T>(item, comparer));

		Set<EquatableWrapper<T>> actualSet = new HashSet<EquatableWrapper<T>>();
		for(T item: actual)
			actualSet.add(new EquatableWrapper<T>(item, comparer));

		Assert.assertEquals(expectedSet, actualSet);
	}	
	
}
