package org.qora.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.settings.Settings;

public class NTP {

	private static final Logger LOGGER = LogManager.getLogger(NTP.class);
	private static final double MAX_STDDEV = 25; // ms

	/**
	 * Returns aggregated internet time.
	 * 
	 * @return internet time (ms), or null if unsuccessful.
	 */
	public static Long getTime() {
		Long meanOffset = getOffset();
		if (meanOffset == null)
			return null;

		return System.currentTimeMillis() + meanOffset;
	}

	/**
	 * Returns mean offset from internet time.
	 * 
	 * Positive offset means local clock is behind internet time.
	 * 
	 * @return offset (ms), or null if unsuccessful.
	 */
	public static Long getOffset() {
		String[] ntpServers = Settings.getInstance().getNtpServers();

		NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(2000);

		List<Double> offsets = new ArrayList<>();

		for (String server : ntpServers) {
			try {
				TimeInfo timeInfo = client.getTime(InetAddress.getByName(server));

				timeInfo.computeDetails();

				LOGGER.debug(() -> String.format("%c%16.16s %16.16s %2d %c %4d %4d  %3o %6dms % 5dms % 5dms",
						' ',
						server,
						timeInfo.getMessage().getReferenceIdString(),
						timeInfo.getMessage().getStratum(),
						'u',
						0,
						1 << timeInfo.getMessage().getPoll(),
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

		if (offsets.size() < ntpServers.length / 2) {
			LOGGER.debug("Not enough replies");
			return null;
		}

		// sₙ represents sum of offsetⁿ
		double s0 = 0;
		double s1 = 0;
		double s2 = 0;

		for (Double offset : offsets) {
			s0 += 1;
			s1 += offset;
			s2 += offset * offset;
		}

		double mean = s1 / s0;
		double stddev = Math.sqrt(((s0 * s2) - (s1 * s1)) / (s0 * (s0 - 1)));

		// If stddev is excessive then we're not very sure so give up
		if (stddev > MAX_STDDEV)
			return null;

		return (long) mean;
	}

}
