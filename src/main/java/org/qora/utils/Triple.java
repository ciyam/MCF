package org.qora.utils;

public class Triple<T, U, V> {

	private T a;
	private U b;
	private V c;

	public Triple() {
	}

	public Triple(T a, U b, V c) {
		this.a = a;
		this.b = b;
		this.c = c;
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

	public void setC(V c) {
		this.c = c;
	}

	public V getC() {
		return c;
	}

}
