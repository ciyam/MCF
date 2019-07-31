package org.qora.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;

public class NTPTests {

	private static final List<String> CC_TLDS = Arrays.asList("oceania", "europe", "cn", "asia", "africa");

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(2000);

		class NTPServer {
			private static final int MIN_POLL = 8;

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
			}

			public Integer getWhen() {
				if (this.lastGood == null)
					return null;

				return (int) ((System.currentTimeMillis() - this.lastGood) / 1000);
			}
		}

		List<NTPServer> ntpServers = new ArrayList<>();

		for (String ccTld : CC_TLDS)
			for (int subpool = 0; subpool <=3; ++subpool)
				ntpServers.add(new NTPServer(subpool + "." + ccTld + ".pool.ntp.org"));

		while (true) {
			Thread.sleep(1000);

			CompletionService<Boolean> ecs = new ExecutorCompletionService<Boolean>(Executors.newCachedThreadPool());
			for (NTPServer server : ntpServers)
				ecs.submit(() -> server.poll(client));

			boolean showReport = false;
			for (int i = 0; i < ntpServers.size(); ++i)
				try {
					showReport = ecs.take().get() || showReport;
				} catch (ExecutionException e) {
					// skip
				}

			if (showReport) {
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
					System.out.println("Not enough replies to calculate network time");
				} else {
					double filterStddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));
					double filterMean = s1 / s0;

					// Now only consider offsets within 1 stddev?
					s0 = 0;
					s1 = 0;
					s2 = 0;

					for (NTPServer server : ntpServers) {
						if (server.offset == null || server.reach == 0)
							continue;

						if (Math.abs(server.offset * (double)server.stratum - filterMean) > filterStddev)
							continue;

						server.usage = '*';
						s0 += 1;
						s1 += server.offset;
						s2 += server.offset * server.offset;
					}

					if (s0 <= 1) {
						System.out.println(String.format("Not enough values to calculate network time. stddev: %7.4f", filterStddev));
					} else {
						double mean = s1 / s0;
						double newStddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));
						System.out.println(String.format("filtering stddev: %7.3f, mean: %7.3f, new stddev: %7.3f, nValues: %.0f / %d", filterStddev, mean, newStddev, s0, ntpServers.size()));
					}
				}

				System.out.println(String.format("%c%16s %16s %2s %c %4s %4s %3s %7s %7s %7s",
						' ', "remote", "refid", "st", 't', "when", "poll", "reach", "delay", "offset", "jitter"
				));

				for (NTPServer server : ntpServers)
					System.out.println(String.format("%c%16.16s %16.16s %2s %c %4s %4d  %3o  %7s %7s %7s",
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

	private static String formatNull(String format, Object arg, String nullOutput) {
		return arg != null ? String.format(format, arg) : nullOutput;
	}

}
