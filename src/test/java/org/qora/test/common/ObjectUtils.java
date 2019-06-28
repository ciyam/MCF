package org.qora.test.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ObjectUtils {

	public static Object callMethod(Object obj, String methodName, Object... args) {
		Method[] methods = obj.getClass().getDeclaredMethods();

		Method foundMethod = Arrays.stream(methods).filter(method -> method.getName().equals(methodName)).findFirst().get();

		try {
			return foundMethod.invoke(obj, args);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("method call failed", e);
		}
	}

}
