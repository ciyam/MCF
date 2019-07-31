package org.qora.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.settings.Settings;

public class NTP implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger(NTP.class);

	private static boolean isStarted = false;
	private static volatile boolean isStopping = false;
	private static ExecutorService instanceExecutor;
	private static NTP instance;
	private static volatile Long offset = null;

	static class NTPServer {
		private static final int MIN_POLL = 64;

		public char usage = ' ';
		public String remote;
		public String refId;
		public Integer stratum;
		public char type = 'u'; // unicast
		public int poll = MIN_POLL;
		public byte reach = 0;
		public Long delay;
		public Double offset;
		public Double jitter;

		private Deque<Double> offsets = new LinkedList<>();
		private double totalSquareOffsets = 0.0;
		private long nextPoll;
		private Long lastGood;

		public NTPServer(String remote) {
			this.remote = remote;
		}

		public boolean poll(NTPUDPClient client) {
			Thread.currentThread().setName(String.format("NTP: %s", this.remote));

			try {
				final long now = System.currentTimeMillis();

				if (now < this.nextPoll)
					return false;

				boolean isUpdated = false;
				try {
					TimeInfo timeInfo = client.getTime(InetAddress.getByName(remote));

					timeInfo.computeDetails();
					NtpV3Packet ntpMessage = timeInfo.getMessage();

					this.refId = ntpMessage.getReferenceIdString();
					this.stratum = ntpMessage.getStratum();
					this.poll = Math.max(MIN_POLL, 1 << ntpMessage.getPoll());

					this.delay = timeInfo.getDelay();
					this.offset = (double) timeInfo.getOffset();

					if (this.offsets.size() == 8) {
						double oldOffset = this.offsets.removeFirst();
						this.totalSquareOffsets -= oldOffset * oldOffset;
					}

					this.offsets.addLast(this.offset);
					this.totalSquareOffsets += this.offset * this.offset;

					this.jitter = Math.sqrt(this.totalSquareOffsets / this.offsets.size());

					this.reach = (byte) ((this.reach << 1) | 1);
					this.lastGood = now;

					isUpdated = true;
				} catch (IOException e) {
					this.reach <<= 1;
				}

				this.nextPoll = now + this.poll * 1000;
				return isUpdated;
			} finally {
				Thread.currentThread().setName("NTP (dormant)");
			}
		}

		public Integer getWhen() {
			if (this.lastGood == null)
				return null;

			return (int) ((System.currentTimeMillis() - this.lastGood) / 1000);
		}
	}

	private final NTPUDPClient client;
	private List<NTPServer> ntpServers = new ArrayList<>();
	private final ExecutorService serverExecutor;

	private NTP() {
		client = new NTPUDPClient();
		client.setDefaultTimeout(2000);

		for (String serverName : Settings.getInstance().getNtpServers())
			ntpServers.add(new NTPServer(serverName));

		serverExecutor = Executors.newCachedThreadPool();
	}

	public static synchronized void start() {
		if (isStarted)
			return;

		instanceExecutor = Executors.newSingleThreadExecutor();
		instance = new NTP();
		instanceExecutor.execute(instance);
	}

	public static void shutdownNow() {
		instanceExecutor.shutdownNow();
	}

	/**
	 * Returns our estimate of internet time.
	 * 
	 * @return internet time (ms), or null if unsynchronized.
	 */
	public static Long getTime() {
		if (offset == null)
			return null;

		return System.currentTimeMillis() + offset;
	}

	public void run() {
		Thread.currentThread().setName("NTP instance");

		try {
			while (!isStopping) {
				Thread.sleep(1000);

				CompletionService<Boolean> ecs = new ExecutorCompletionService<Boolean>(serverExecutor);
				for (NTPServer server : ntpServers)
					ecs.submit(() -> server.poll(client));

				boolean hasUpdate = false;
				for (int i = 0; i < ntpServers.size(); ++i) {
					if (isStopping)
						return;

					try {
						hasUpdate = ecs.take().get() || hasUpdate;
					} catch (ExecutionException e) {
						// skip
					}
				}

				if (hasUpdate) {
					double s0 = 0;
					double s1 = 0;
					double s2 = 0;

					for (NTPServer server : ntpServers) {
						if (server.offset == null) {
							server.usage = ' ';
							continue;
						}

						server.usage = '+';
						double value = server.offset * (double) server.stratum;

						s0 += 1;
						s1 += value;
						s2 += value * value;
					}

					if (s0 < ntpServers.size() / 3 + 1) {
						LOGGER.debug(String.format("Not enough replies (%d) to calculate network time", s0));
					} else {
						double thresholdStddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));
						double mean = s1 / s0;

						// Now only consider offsets within 1 stddev?
						s0 = 0;
						s1 = 0;
						s2 = 0;

						for (NTPServer server : ntpServers) {
							if (server.offset == null || server.reach == 0)
								continue;

							if (Math.abs(server.offset * (double)server.stratum - mean) > thresholdStddev)
								continue;

							server.usage = '*';
							s0 += 1;
							s1 += server.offset;
							s2 += server.offset * server.offset;
						}

						if (s0 <= 1) {
							LOGGER.debug(String.format("Not enough useful values (%d) to calculate network time. (stddev: %7.4f)", s0, thresholdStddev));
						} else {
							double filteredMean = s1 / s0;
							double filteredStddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));

							LOGGER.trace(String.format("Threshold stddev: %7.3f, mean: %7.3f, stddev: %7.3f, nValues: %.0f / %d",
									thresholdStddev, filteredMean, filteredStddev, s0, ntpServers.size()));

							NTP.offset = (long) filteredMean;
							LOGGER.debug(String.format("New NTP offset: %d", NTP.offset));
						}
					}

					if (LOGGER.getLevel().isMoreSpecificThan(Level.TRACE)) {
						LOGGER.trace(String.format("%c%16s %16s %2s %c %4s %4s %3s %7s %7s %7s",
								' ', "remote", "refid", "st", 't', "when", "poll", "reach", "delay", "offset", "jitter"
						));

						for (NTPServer server : ntpServers)
							LOGGER.trace(String.format("%c%16.16s %16.16s %2s %c %4s %4d  %3o  %7s %7s %7s",
									server.usage,
									server.remote,
									formatNull("%s", server.refId, ""),
									formatNull("%2d", server.stratum, ""),
									server.type,
									formatNull("%4d", server.getWhen(), "-"),
									server.poll,
									server.reach,
									formatNull("%5dms", server.delay, ""),
									formatNull("% 5.0fms", server.offset, ""),
									formatNull("%5.2fms", server.jitter, "")
							));
					}
				}
			}
		} catch (InterruptedException e) {
			// Exit
		}
	}

	private static String formatNull(String format, Object arg, String nullOutput) {
		return arg != null ? String.format(format, arg) : nullOutput;
	}

}
