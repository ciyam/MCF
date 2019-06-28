package org.qora.utils;

import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NTP {

	private static final Logger LOGGER = LogManager.getLogger(NTP.class);
	private static final long TIME_TILL_UPDATE = 1000 * 60 * 10;
	private static final String NTP_SERVER = "pool.ntp.org";

	private static long lastUpdate = 0;
	private static long offset = 0;

	/** Returns NTP-synced current time from unix epoch, in milliseconds. */
	public static long getTime() {
		// Every so often use NTP to find out offset between this system's time and internet time
		if (System.currentTimeMillis() > lastUpdate + TIME_TILL_UPDATE) {
			updateOffset();
			lastUpdate = System.currentTimeMillis();

			// Log new value of offset
			// TODO: LOGGER.info(Lang.getInstance().translate("Adjusting time with %offset% milliseconds.").replace("%offset%", String.valueOf(offset)));
			LOGGER.info("Adjusting time with %offset% milliseconds.".replace("%offset%", String.valueOf(offset)));
		}

		// Return time that is nearer internet time
		return System.currentTimeMillis() + offset;
	}

	private static void updateOffset() {
		// Create NTP client
		NTPUDPClient client = new NTPUDPClient();

		// Set communications timeout
		client.setDefaultTimeout(10000);
		try {
			// Open client (create socket, etc.)
			client.open();

			// Get time info from NTP server
			InetAddress hostAddr = InetAddress.getByName(NTP_SERVER);
			TimeInfo info = client.getTime(hostAddr);
			info.computeDetails();

			// Cache offset between this system's time and internet time
			if (info.getOffset() != null)
				offset = info.getOffset();
		} catch (Exception e) {
			// Error while communicating with NTP server - ignored
		}

		// We're done with NTP client
		client.close();
	}

}
