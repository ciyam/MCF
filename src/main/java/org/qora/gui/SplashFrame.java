package org.qora.gui;

import java.awt.BorderLayout;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JDialog;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SplashFrame {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);

	private static SplashFrame instance;
	private JDialog splashDialog;

	@SuppressWarnings("serial")
	public static class SplashPanel extends JPanel {
		private BufferedImage image;

		public SplashPanel() {
			image = GUI.loadImage("splash.png");
			this.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
			this.setLayout(new BorderLayout());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(image, 0, 0, null);
		}
	}

	private SplashFrame() {
		this.splashDialog = new JDialog();

		List<Image> icons = new ArrayList<Image>();
		icons.add(GUI.loadImage("icons/icon16.png"));
		icons.add(GUI.loadImage("icons/icon32.png"));
		icons.add(GUI.loadImage("icons/icon64.png"));
		icons.add(GUI.loadImage("icons/icon128.png"));
		this.splashDialog.setIconImages(icons);

		this.splashDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		this.splashDialog.setTitle("MCF");
		this.splashDialog.setContentPane(new SplashPanel());

		this.splashDialog.setUndecorated(true);
		this.splashDialog.setModal(false);
		this.splashDialog.pack();
		this.splashDialog.setLocationRelativeTo(null);
		this.splashDialog.toFront();
		this.splashDialog.setVisible(true);
		this.splashDialog.repaint();
	}

	public static SplashFrame getInstance() {
		if (instance == null)
			instance = new SplashFrame();

		return instance;
	}

	public void setVisible(boolean b) {
		this.splashDialog.setVisible(b);
	}

	public void dispose() {
		this.splashDialog.dispose();
	}

}
