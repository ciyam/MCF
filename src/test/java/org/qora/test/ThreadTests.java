package org.qora.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.qora.utils.ExecuteProduceConsume;

public class ThreadTests {

	private void testEPC(ExecuteProduceConsume testEPC) throws InterruptedException {
		testEPC.start();

		// Let it run for a minute
		for (int s = 1; s <= 60; ++s) {
			Thread.sleep(1000);
			System.out.println(String.format("After %d second%s, active threads: %d, greatest thread count: %d", s, (s != 1 ? "s" : "") , testEPC.getActiveThreadCount(), testEPC.getGreatestActiveThreadCount()));
		}

		final long before = System.currentTimeMillis();
		testEPC.shutdown(30 * 1000);
		final long after = System.currentTimeMillis();

		System.out.println(String.format("Shutdown took %d milliseconds", after - before));
		System.out.println(String.format("Greatest thread count: %d", testEPC.getGreatestActiveThreadCount()));
	}

	@Test
	public void testRandomEPC() throws InterruptedException {
		final int TASK_PERCENT = 25; // Produce a task this % of the time
		final int PAUSE_PERCENT = 80; // Pause for new work this % of the time

		class RandomEPC extends ExecuteProduceConsume {
			@Override
			protected Task produceTask(boolean canIdle) throws InterruptedException {
				Random random = new Random();

				final int percent = random.nextInt(100);

				// Sometimes produce a task
				if (percent < TASK_PERCENT) {
					return new Task() {
						@Override
						public void perform() throws InterruptedException {
							Thread.sleep(random.nextInt(500) + 100);
						}
					};
				} else {
					// If we don't produce a task, then maybe simulate a pause until work arrives
					if (canIdle && percent < PAUSE_PERCENT)
						Thread.sleep(random.nextInt(100));

					return null;
				}
			}
		}

		testEPC(new RandomEPC());
	}

	/**
	 * Test ping scenario with many peers requiring pings.
	 * <p>
	 * Specifically, if:
	 * <ul>
	 * <li>the idling EPC thread sleeps for 1 second</li>
	 * <li>pings are required every P seconds</li>
	 * <li>there are way more than P peers</li>
	 * </ul>
	 * then we need to make sure EPC threads are not
	 * delayed such that some peers (>P) don't get a
	 * chance to be pinged.
	 */
	@Test
	public void testPingEPC() throws InterruptedException {
		final long PRODUCER_SLEEP_TIME = 1000; // ms
		final long PING_INTERVAL = PRODUCER_SLEEP_TIME * 8; // ms
		final long PING_ROUND_TRIP_TIME = PRODUCER_SLEEP_TIME * 5; // ms

		final int MAX_PEERS = 20;

		final List<Long> lastPings = new ArrayList<>(Collections.nCopies(MAX_PEERS, System.currentTimeMillis()));

		class PingTask implements ExecuteProduceConsume.Task {
			private final int peerIndex;

			public PingTask(int peerIndex) {
				this.peerIndex = peerIndex;
			}

			@Override
			public void perform() throws InterruptedException {
				System.out.println("Pinging peer " + peerIndex);

				// At least half the worst case ping round-trip
				Random random = new Random();
				int halfTime = (int) PING_ROUND_TRIP_TIME / 2;
				long sleep = random.nextInt(halfTime) + halfTime;
				Thread.sleep(sleep);
			}
		}

		class PingEPC extends ExecuteProduceConsume {
			@Override
			protected Task produceTask(boolean canIdle) throws InterruptedException {
				// If we can idle, then we do, to simulate worst case
				if (canIdle)
					Thread.sleep(PRODUCER_SLEEP_TIME);

				// Is there a peer that needs a ping?
				final long now = System.currentTimeMillis();
				synchronized (lastPings) {
					for (int peerIndex = 0; peerIndex < lastPings.size(); ++peerIndex) {
						long lastPing = lastPings.get(peerIndex);

						if (lastPing < now - PING_INTERVAL - PING_ROUND_TRIP_TIME - PRODUCER_SLEEP_TIME)
							throw new RuntimeException("excessive peer ping interval for peer " + peerIndex);

						if (lastPing < now - PING_INTERVAL) {
							lastPings.set(peerIndex, System.currentTimeMillis());
							return new PingTask(peerIndex);
						}
					}
				}

				// No work to do
				return null;
			}
		}

		testEPC(new PingEPC());
	}

}
