package org.qora.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;

public class NTPTests {

	private static final List<String> CC_TLDS = Arrays.asList("oceania", "europe", "lat", "asia", "africa");

	public static void main(String[] args) throws UnknownHostException, IOException {
		NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(2000);

		System.out.println(String.format("%c%16s %16s %2s %c %4s %4s %3s %7s %7s %7s",
				' ', "remote", "refid", "st", 't', "when", "poll", "reach", "delay", "offset", "jitter"
		));

		List<Double> offsets = new ArrayList<>();

		List<String> ntpServers = new ArrayList<>();
		for (String ccTld : CC_TLDS) {
			ntpServers.add(ccTld + ".pool.ntp.org");
			for (int subpool = 0; subpool <=3; ++subpool)
				ntpServers.add(subpool + "." + ccTld + ".pool.ntp.org");
		}

		for (String server : ntpServers) {
			try {
				TimeInfo timeInfo = client.getTime(InetAddress.getByName(server));

				timeInfo.computeDetails();
				NtpV3Packet ntpMessage = timeInfo.getMessage();

				System.out.println(String.format("%c%16.16s %16.16s %2d %c %4d %4d  %3o %6dms % 5dms % 5dms",
						' ',
						server,
						ntpMessage.getReferenceIdString(),
						ntpMessage.getStratum(),
						'u',
						0,
						1 << ntpMessage.getPoll(),
						1,
						timeInfo.getDelay(),
						timeInfo.getOffset(),
						0
				));

				offsets.add((double) timeInfo.getOffset());
			} catch (IOException e) {
				// Try next server...
			}
		}

		if (offsets.size() < ntpServers.size() / 2) {
			System.err.println("Not enough replies");
			System.exit(1);
		}

		double s0 = 0;
		double s1 = 0;
		double s2 = 0;

		for (Double offset : offsets) {
			// Exclude nearby results for more extreme testing
			if (offset < 100.0)
				continue;

			s0 += 1;
			s1 += offset;
			s2 += offset * offset;
		}

		double mean = s1 / s0;
		double stddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));
		System.out.println(String.format("mean: %7.3f, stddev: %7.3f", mean, stddev));
	}

}
