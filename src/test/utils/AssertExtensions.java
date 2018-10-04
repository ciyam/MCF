package test.utils;

import com.google.common.collect.Iterables;
import java.lang.reflect.Array;
import java.lang.Class;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.MatcherAssert.assertThat;

public class AssertExtensions {

	public static <T> void assertItemsEqual(Iterable<T> expected, Iterable<T> actual, EqualityComparer<T> comparer) {
		assertItemsEqual(expected, actual, comparer, (String)null);
	}
	
	public static <T> void assertItemsEqual(Iterable<T> expected, Iterable<T> actual, EqualityComparer<T> comparer, String message) {
		List<EquatableWrapper<T>> expectedSet = new ArrayList<EquatableWrapper<T>>();
		for(T item: expected)
			expectedSet.add(new EquatableWrapper<T>(item, comparer));

		List<EquatableWrapper<T>> actualSet = new ArrayList<EquatableWrapper<T>>();
		for(T item: actual)
			actualSet.add(new EquatableWrapper<T>(item, comparer));

		assertItemsEqual(expectedSet, actualSet, message);
	}	
	
	public static <T> void assertItemsEqual(Iterable<T> expected, Iterable<T> actual) {
		assertItemsEqual(expected, actual, (String)null);
	}
	
	public static <T> void assertItemsEqual(Iterable<T> expected, Iterable<T> actual, String message) {
		List<T> list = new ArrayList<T>();
		T[] expectedArray = getArray(expected);
		assertThat(message, actual, containsInAnyOrder(expectedArray));
	}	
	
	private static <T> T[] getArray(Iterable<T> iterable) {
		// XXX: What a horrific way to create an array from an iterable.
		// Isn't there a better solution?
		List<T> list = new ArrayList<T>();
		for(T item : iterable)
			list.add(item);
		@SuppressWarnings("unchecked")
		T[] result = (T[])new Object[list.size()];
		for(int i = 0; i < list.size(); i++)
			result[i] = list.get(i);
		return result;
	}
}
