package test.utils;

import java.util.Collection;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.MatcherAssert.assertThat;

public class AssertExtensions {

	public static <T> void assertItemsEqual(Collection<T> expected, Iterable<T> actual) {
		assertItemsEqual(expected, actual, (String) null);
	}

	public static <T> void assertItemsEqual(Collection<T> expected, Iterable<T> actual, String message) {
		assertThat(message, actual, containsInAnyOrder(expected.toArray()));
	}

}
