package test.utils;

public interface EqualityComparer<T> {
	boolean equals(T first, T second);
	int hashCode(T item);
}
