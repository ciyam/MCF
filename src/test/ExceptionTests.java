package test;

import static org.junit.Assert.*;

import org.junit.Test;
import qora.block.Block;

public class ExceptionTests {

	/**
	 * Proof of concept for block processing throwing transaction-related SQLException rather than savepoint-rollback-related SQLException.
	 * <p>
	 * See {@link Block#isValid(Connection)}.
	 */
	@Test
	public void testBlockProcessingExceptions() {
		try {
			simulateThrow();
			fail("Should not return result");
		} catch (Exception e) {
			assertEquals("Transaction issue", e.getMessage());
		}

		try {
			boolean result = simulateFalse();
			assertFalse(result);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}

		try {
			boolean result = simulateTrue();
			assertTrue(result);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}

	}

	public boolean simulateThrow() throws Exception {
		// simulate create savepoint (no-op)

		try {
			// simulate processing transactions but an exception is thrown
			throw new Exception("Transaction issue");
		} finally {
			// attempt to rollback
			try {
				// simulate failing to rollback due to prior exception
				throw new Exception("Rollback issue");
			} catch (Exception e) {
				// test discard of rollback exception, leaving prior exception
			}
		}
	}

	public boolean simulateFalse() throws Exception {
		// simulate create savepoint (no-op)

		try {
			// simulate processing transactions but false returned
			return false;
		} finally {
			// attempt to rollback
			try {
				// simulate successful rollback (no-op)
			} catch (Exception e) {
				// test discard of rollback exception, leaving prior exception
			}
		}
	}

	public boolean simulateTrue() throws Exception {
		// simulate create savepoint (no-op)

		try {
			// simulate processing transactions successfully
		} finally {
			// attempt to rollback
			try {
				// simulate successful rollback (no-op)
			} catch (Exception e) {
				// test discard of rollback exception, leaving prior exception
			}
		}

		return true;
	}

}
