package org.qora.test.common;

import java.lang.reflect.Field;

import org.eclipse.jetty.server.Request;
import org.junit.Before;
import org.qora.repository.DataException;

public class ApiCommon extends Common {

	public static final Boolean[] ALL_BOOLEAN_VALUES = new Boolean[] { null, true, false };
	public static final Boolean[] TF_BOOLEAN_VALUES = new Boolean[] { true, false };

	public static class FakeRequest extends Request {
		public FakeRequest() {
			super(null, null);
		}

		@Override
		public String getRemoteAddr() {
			return "127.0.0.1";
		}
	}
	private static final FakeRequest FAKE_REQUEST = new FakeRequest();

	public String aliceAddress;

	@Before
	public void beforeTests() throws DataException {
		Common.useDefaultSettings();

		this.aliceAddress = Common.getTestAccount(null, "alice").getAddress();
	}

	public static Object buildResource(Class<?> resourceClass) {
		try {
			Object resource = resourceClass.newInstance();

			Field requestField = resourceClass.getDeclaredField("request");
			requestField.setAccessible(true);
			requestField.set(resource, FAKE_REQUEST);

			return resource;
		} catch (Exception e) {
			throw new RuntimeException("Failed to build API resource " + resourceClass.getName() + ": " + e.getMessage(), e);
		}
	}

}
