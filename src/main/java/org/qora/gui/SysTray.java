package org.qora.gui;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.controller.Controller;
import org.qora.globalization.Translator;
import org.qora.settings.Settings;
import org.qora.ui.UiService;
import org.qora.utils.URLViewer;

public class SysTray {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);
	private static final String NTP_SCRIPT = "ntpcfg.bat";

	private static SysTray instance;
	private TrayIcon trayIcon = null;
	private JPopupMenu popupMenu = null;
	/** The hidden dialog has 'focus' when menu displayed so closes the menu when user clicks elsewhere. */
	private JDialog hiddenDialog = null;

	private SysTray() {
		if (!SystemTray.isSupported())
			return;

		LOGGER.info("Launching system tray icon");

		this.popupMenu = createJPopupMenu();

		// Build TrayIcon without AWT PopupMenu (which doesn't support Unicode)...
		this.trayIcon = new TrayIcon(Gui.loadImage("icons/icon32.png"), "MCF", null);
		// ...and attach mouse listener instead so we can use JPopupMenu (which does support Unicode)
		this.trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed (MouseEvent me) {
				this.maybePopupMenu(me);
			}

			@Override
			public void mouseReleased (MouseEvent me) {
				this.maybePopupMenu(me);
			}

			private void maybePopupMenu(MouseEvent me) {
				if (me.isPopupTrigger()) {
					// We destroy, then recreate, the hidden dialog to prevent taskbar entries on X11
					if (!popupMenu.isVisible())
						destroyHiddenDialog();

					createHiddenDialog();
					hiddenDialog.setLocation(me.getX() + 1, me.getY() - 1);
					popupMenu.setLocation(me.getX() + 1, me.getY() - 1);

					popupMenu.setInvoker(hiddenDialog);

					hiddenDialog.setVisible(true);
					popupMenu.setVisible(true);
				}
			}
		});

		this.trayIcon.setImageAutoSize(true);

		try {
			SystemTray.getSystemTray().add(this.trayIcon);
		} catch (AWTException e) {
			this.trayIcon = null;
		}
	}

	private void createHiddenDialog() {
		if (hiddenDialog != null)
			return;

		hiddenDialog = new JDialog();
		hiddenDialog.setUndecorated(true);
		hiddenDialog.setSize(10, 10);
		hiddenDialog.addWindowFocusListener(new WindowFocusListener () {
			@Override
			public void windowLostFocus (WindowEvent we ) {
				destroyHiddenDialog();
			}

			@Override
			public void windowGainedFocus (WindowEvent we) {
			}
		});
	}

	private void destroyHiddenDialog() {
		if (hiddenDialog == null)
			return;

		hiddenDialog.setVisible(false);
		hiddenDialog.dispose();
		hiddenDialog = null;
	}

	private JPopupMenu createJPopupMenu() {
		JPopupMenu menu = new JPopupMenu();

		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				destroyHiddenDialog();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});

		JMenuItem openUi = new JMenuItem(Translator.INSTANCE.translate("SysTray", "OPEN_NODE_UI"));
		openUi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				destroyHiddenDialog();

				try {
					URLViewer.openWebpage(new URL("http://localhost:" + Settings.getInstance().getUiPort()));
				} catch (Exception e1) {
					LOGGER.error("Unable to open node UI in browser");
				}
			}
		});
		menu.add(openUi);

		JMenuItem openTimeCheck = new JMenuItem(Translator.INSTANCE.translate("SysTray", "CHECK_TIME_ACCURACY"));
		openTimeCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				destroyHiddenDialog();

				try {
					URLViewer.openWebpage(new URL("https://time.is"));
				} catch (Exception e1) {
					LOGGER.error("Unable to open time-check website in browser");
				}
			}
		});
		menu.add(openTimeCheck);

		// Only for Windows users
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			JMenuItem syncTime = new JMenuItem(Translator.INSTANCE.translate("SysTray", "SYNCHRONIZE_CLOCK"));
			syncTime.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					destroyHiddenDialog();

					new SynchronizeWorker().execute();
				}
			});
			menu.add(syncTime);
		}

		JMenuItem exit = new JMenuItem(Translator.INSTANCE.translate("SysTray", "EXIT"));
		exit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				destroyHiddenDialog();

				new ClosingWorker().execute();
			}
		});
		menu.add(exit);

		return menu;
	}

	class SynchronizeWorker extends SwingWorker<Void, Void> {
		@Override
		protected Void doInBackground() {
			// Extract reconfiguration script from resources
			String resourceName = "/" + UiService.DOWNLOADS_RESOURCE_PATH + "/" + NTP_SCRIPT;
			Path scriptPath = Paths.get(NTP_SCRIPT);

			try (InputStream in = SysTray.class.getResourceAsStream(resourceName)) {
				Files.copy(in, scriptPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IllegalArgumentException | IOException e) {
				LOGGER.warn(String.format("Couldn't locate NTP configuration resource: %s", resourceName));
				return null;
			}

			// Now execute extracted script
			List<String> scriptCmd = Arrays.asList(NTP_SCRIPT);
			LOGGER.info(String.format("Running NTP configuration script: %s", String.join(" ", scriptCmd)));
			try {
				new ProcessBuilder(scriptCmd).start();
			} catch (IOException e) {
				LOGGER.warn(String.format("Failed to execute NTP configuration script: %s", e.getMessage()));
				return null;
			}

			return null;
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

	public static synchronized SysTray getInstance() {
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
