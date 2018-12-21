package utils;

public class Pair<T, U> {

	private T a;
	private U b;

	public Pair() {
	}

	public Pair(T a, U b) {
		this.a = a;
		this.b = b;
	}

	public void setA(T a) {
		this.a = a;
	}

	public T getA() {
		return a;
	}

	public void setB(U b) {
		this.b = b;
	}

	public U getB() {
		return b;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (!(o instanceof Pair<?, ?>))
			return false;

		Pair<?, ?> other = (Pair<?, ?>) o;

		return this.a.equals(other.getA()) && this.b.equals(other.getB());
	}

	@Override
	public int hashCode() {
		return this.a.hashCode() ^ this.b.hashCode();
	}

}
