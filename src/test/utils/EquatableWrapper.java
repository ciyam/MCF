package test.utils;

class EquatableWrapper<T> {

	private final T item;
	private final EqualityComparer<T> comparer;

	public EquatableWrapper(T item, EqualityComparer<T> comparer) {
		this.item = item;
		this.comparer = comparer;			
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null)
			return false;
		if (!(this.getClass().isInstance(obj)))
			return false;
		EquatableWrapper<T> otherWrapper = (EquatableWrapper<T>)obj;
		if (otherWrapper.item == this.item)
			return true;
		return this.comparer.equals(this.item, otherWrapper.item);
	}

	@Override
	public int hashCode() {
		return this.comparer.hashCode(this.item);
	}
	
	@Override
	public String toString() {
		return this.item.toString();
	}
}