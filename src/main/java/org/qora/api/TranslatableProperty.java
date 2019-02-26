package org.qora.api;

interface TranslatableProperty<T> {
	public String keyName();
	public void setValue(T item, String translation);
	public String getValue(T item);
}
