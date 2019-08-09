package org.qora.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ExecuteProduceConsume implements Runnable {

	private final String className;
	private final Logger logger;

	private ExecutorService executor;
	private int activeThreadCount = 0;
	private int greatestActiveThreadCount = 0;
	private int consumerCount = 0;

	private boolean hasThreadPending = false;

	public ExecuteProduceConsume(ExecutorService executor) {
		className = this.getClass().getSimpleName();
		logger = LogManager.getLogger(this.getClass());

		this.executor = executor;
	}

	public ExecuteProduceConsume() {
		this(Executors.newCachedThreadPool());
	}

	public void start() {
		executor.execute(this);
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	public boolean shutdown(long timeout) throws InterruptedException {
		executor.shutdownNow();
		return executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
	}

	public int getActiveThreadCount() {
		synchronized (this) {
			return activeThreadCount;
		}
	}

	public int getGreatestActiveThreadCount() {
		synchronized (this) {
			return greatestActiveThreadCount;
		}
	}

	/**
	 * Returns a Task to be performed, possibly blocking.
	 * 
	 * @param canBlock
	 * @return task to be performed, or null if no task pending.
	 * @throws InterruptedException
	 */
	protected abstract Task produceTask(boolean canBlock) throws InterruptedException;

	@FunctionalInterface
	public interface Task {
		public abstract void perform() throws InterruptedException;
	}

	@Override
	public void run() {
		Thread.currentThread().setName(className + "-" + Thread.currentThread().getId());

		boolean wasThreadPending;
		synchronized (this) {
			++activeThreadCount;
			if (activeThreadCount > greatestActiveThreadCount)
				greatestActiveThreadCount = activeThreadCount;

			logger.trace(() -> String.format("[%d] started, hasThreadPending was: %b, activeThreadCount now: %d",
					Thread.currentThread().getId(), hasThreadPending, activeThreadCount));

			// Defer clearing hasThreadPending to prevent unnecessary threads waiting to produce...
			wasThreadPending = hasThreadPending;
		}

		try {
			boolean canBlock = false;

			while (true) {
				final Task task;

				logger.trace(() -> String.format("[%d] waiting to produce...", Thread.currentThread().getId()));

				synchronized (this) {
					if (wasThreadPending) {
						// Clear thread-pending flag now that we about to produce.
						hasThreadPending = false;
						wasThreadPending = false;
					}

					final boolean lambdaCanIdle = canBlock;
					logger.trace(() -> String.format("[%d] producing, activeThreadCount: %d, consumerCount: %d, canBlock is %b...",
							Thread.currentThread().getId(), activeThreadCount, consumerCount, lambdaCanIdle));

					final long now = System.currentTimeMillis();
					task = produceTask(canBlock);
					final long delay = System.currentTimeMillis() - now;
					logger.trace(() -> String.format("[%d] producing took %dms", Thread.currentThread().getId(), delay));
				}

				if (task == null)
					synchronized (this) {
						logger.trace(() -> String.format("[%d] no task, activeThreadCount: %d, consumerCount: %d",
								Thread.currentThread().getId(), activeThreadCount, consumerCount));

						if (activeThreadCount > consumerCount + 1) {
							--activeThreadCount;
							logger.trace(() -> String.format("[%d] ending, activeThreadCount now: %d", Thread.currentThread().getId(), activeThreadCount));
							break;
						}

						// We're the last surviving thread - producer can afford to block next round
						canBlock = true;

						continue;
					}

				// We have a task

				synchronized (this) {
					++consumerCount;

					if (!hasThreadPending) {
						logger.trace(() -> String.format("[%d] spawning another thread", Thread.currentThread().getId()));
						hasThreadPending = true;
						executor.execute(this); // Same object, different thread
					}
				}

				logger.trace(() -> String.format("[%d] performing task...", Thread.currentThread().getId()));
				task.perform(); // This can block for a while
				logger.trace(() -> String.format("[%d] finished task", Thread.currentThread().getId()));

				synchronized (this) {
					--consumerCount;

					// Quicker, non-blocking produce next round
					canBlock = false;
				}
			}
		} catch (InterruptedException | RejectedExecutionException e) {
			// We're in shutdown situation so exit
		} finally {
			Thread.currentThread().setName(className + "-dormant");
		}
	}

}
