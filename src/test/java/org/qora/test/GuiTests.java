package org.qora.test;

import org.junit.Test;
import org.qora.gui.SplashFrame;
import org.qora.gui.SysTray;

public class GuiTests {

	@Test
	public void testSplashFrame() throws InterruptedException {
		SplashFrame splashFrame = SplashFrame.getInstance();

		Thread.sleep(2000L);

		splashFrame.dispose();
	}

	@Test
	public void testSysTray() throws InterruptedException {
		SysTray.getInstance();

		while(true) {
			Thread.sleep(2000L);
		}
	}

}
