package org.qora.gui;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GUI {

	private static final Logger LOGGER = LogManager.getLogger(GUI.class);
	private static GUI instance;

	private boolean isHeadless;
	private SplashFrame splash = null;
	private SysTray sysTray = null;

	private GUI() {
		this.isHeadless = GraphicsEnvironment.isHeadless();

		if (!this.isHeadless)
			showSplash();
	}

	private void showSplash() {
		LOGGER.trace("Splash");
		this.splash = SplashFrame.getInstance();
	}

	protected static BufferedImage loadImage(String resourceName) {
		try (InputStream in = ClassLoader.getSystemResourceAsStream("images/" + resourceName)) {
			return ImageIO.read(in);
		} catch (IOException e) {
			LOGGER.warn(String.format("Couldn't locate image resource \"images/%s\"", resourceName));
			return null;
		}
	}

	public static GUI getInstance() {
		if (instance == null)
			instance = new GUI();

		return instance;
	}

	public void notifyRunning() {
		if (this.isHeadless)
			return;

		this.splash.dispose();
		this.splash = null;

		this.sysTray = SysTray.getInstance();
	}

	public void shutdown() {
		if (this.isHeadless)
			return;

		if (this.splash != null)
			this.splash.dispose();

		if (this.sysTray != null)
			this.sysTray.dispose();
	}

}
