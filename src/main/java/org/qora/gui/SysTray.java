package org.qora.gui;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;

public class SysTray {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);

	private static SysTray instance;
	private TrayIcon trayIcon = null;
	private PopupMenu popupMenu = null;

	@SuppressWarnings("serial")
	public static class SplashPanel extends JPanel {
		private BufferedImage image;

		public SplashPanel() {
			try (InputStream in = ClassLoader.getSystemResourceAsStream("images/splash.png")) {
				image = ImageIO.read(in);
				this.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
				this.setLayout(new BorderLayout());
			} catch (IOException ex) {
				LOGGER.error(ex);
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(image, 0, 0, null);
		}
	}

	private SysTray() {
		if (!SystemTray.isSupported())
			return;

		this.popupMenu = createPopupMenu();

		this.trayIcon = new TrayIcon(GUI.loadImage("icons/icon32.png"), "qora-core", popupMenu);

		this.trayIcon.setImageAutoSize(true);

		try {
			SystemTray.getSystemTray().add(this.trayIcon);
		} catch (AWTException e) {
			this.trayIcon = null;
		}
	}

	class ClosingWorker extends SwingWorker<Void, Void> {
		@Override
		protected Void doInBackground() {
			Controller.getInstance().shutdown();
			return null;
		}

		@Override
		protected void done() {
			System.exit(0);
		}
	}

	private PopupMenu createPopupMenu() {
		PopupMenu menu = new PopupMenu();

		MenuItem exit = new MenuItem("Exit");
		exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new ClosingWorker().execute();
			}
		});
		menu.add(exit);

		return menu;
	}

	public static SysTray getInstance() {
		if (instance == null)
			instance = new SysTray();

		return instance;
	}

	public void showMessage(String caption, String text, TrayIcon.MessageType messagetype) {
		if (trayIcon != null)
			trayIcon.displayMessage(caption, text, messagetype);
	}

	public void setToolTipText(String text) {
		if (trayIcon != null)
			this.trayIcon.setToolTip(text);
	}

	public void dispose() {
		if (trayIcon != null)
			SystemTray.getSystemTray().remove(this.trayIcon);
	}

}
