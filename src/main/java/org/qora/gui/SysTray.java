package org.qora.gui;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.globalization.Translator;
import org.qora.settings.Settings;
import org.qora.utils.URLViewer;

public class SysTray {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);

	private static SysTray instance;
	private TrayIcon trayIcon = null;
	private PopupMenu popupMenu = null;

	private SysTray() {
		if (!SystemTray.isSupported())
			return;

		this.popupMenu = createPopupMenu();

		this.trayIcon = new TrayIcon(Gui.loadImage("icons/icon32.png"), "MCF", popupMenu);

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

		MenuItem openUi = new MenuItem(Translator.INSTANCE.translate("SysTray", "OPEN_NODE_UI"));
		openUi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					URLViewer.openWebpage(new URL("http://localhost:" + Settings.getInstance().getUiPort()));
				} catch (Exception e1) {
					LOGGER.error("Unable to open node UI in browser");
				}
			}
		});
		menu.add(openUi);

		MenuItem exit = new MenuItem(Translator.INSTANCE.translate("SysTray", "EXIT"));
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
