package org.qora.test;

import org.junit.Test;
import org.qora.gui.SplashFrame;

public class GuiTests {

	@Test
	public void testSplashFrame() throws InterruptedException {
		SplashFrame splashFrame = SplashFrame.getInstance();

		Thread.sleep(2000L);

		splashFrame.dispose();
	}

}
