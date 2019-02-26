package org.qora.at;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.at.AT;

public class QoraATLogger implements org.ciyam.at.LoggerInterface {

	// NOTE: We're logging on behalf of qora.at.AT, not ourselves!
	private static final Logger LOGGER = LogManager.getLogger(AT.class);

	@Override
	public void error(String message) {
		LOGGER.error(message);
	}

	@Override
	public void debug(String message) {
		LOGGER.debug(message);
	}

	@Override
	public void echo(String message) {
		LOGGER.info(message);
	}

}
