package org.qora.ui;

import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.qora.settings.Settings;

public class UiService {

	private final Server server;

	public UiService() {
		// Create node management UI server
		this.server = new Server(Settings.getInstance().getUiPort());

		// IP address based access control
		InetAccessHandler accessHandler = new InetAccessHandler();
		for (String pattern : Settings.getInstance().getUiWhitelist()) {
			accessHandler.include(pattern);
		}
		this.server.setHandler(accessHandler);

		// URL rewriting
		RewriteHandler rewriteHandler = new RewriteHandler();
		accessHandler.setHandler(rewriteHandler);

		// Context
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context.setContextPath("/");
		rewriteHandler.setHandler(context);

		// Cross-origin resource sharing
		FilterHolder corsFilterHolder = new FilterHolder(CrossOriginFilter.class);
		corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
		corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST, DELETE");
		corsFilterHolder.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");
		context.addFilter(corsFilterHolder, "/*", null);

		// Node management UI static content servlet
		ServletHolder uiServlet = new ServletHolder("node-management-ui", DefaultServlet.class);
		ClassLoader loader = this.getClass().getClassLoader();
		uiServlet.setInitParameter("resourceBase", loader.getResource("node-management-ui/").toString());
		uiServlet.setInitParameter("dirAllowed", "true");
		uiServlet.setInitParameter("pathInfoOnly", "true");
		context.addServlet(uiServlet, "/*");

		rewriteHandler.addRule(new RedirectPatternRule("", "/peer.html")); // node management UI start page
	}

	private static UiService instance;

	public static UiService getInstance() {
		if (instance == null) {
			instance = new UiService();
		}

		return instance;
	}

	public void start() {
		try {
			// Start server
			server.start();
		} catch (Exception e) {
			// Failed to start
			throw new RuntimeException("Failed to start node management UI", e);
		}
	}

	public void stop() {
		try {
			// Stop server
			server.stop();
		} catch (Exception e) {
			// Failed to stop
		}
	}

}
