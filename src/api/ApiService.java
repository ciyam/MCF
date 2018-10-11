package api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import settings.Settings;

public class ApiService {

	private final Server server;
	private final Set<Class<?>> resources;
	
	public ApiService() {
		// resources to register
		this.resources = new HashSet<Class<?>>();
		this.resources.add(BlocksResource.class);
		this.resources.add(OpenApiResource.class); // swagger
		this.resources.add(AnnotationPostProcessor.class); // for API resource annotations
		ResourceConfig config = new ResourceConfig(this.resources);
		
		// create RPC server
		this.server = new Server(Settings.getInstance().getRpcPort());

		// whitelist
		InetAccessHandler accessHandler = new InetAccessHandler();
		for (String pattern : Settings.getInstance().getRpcAllowed()) {
			accessHandler.include(pattern);
		}
		this.server.setHandler(accessHandler);

		// context
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context.setContextPath("/");
		accessHandler.setHandler(context);

		// API servlet
		ServletContainer container = new ServletContainer(config);
		ServletHolder apiServlet = new ServletHolder(container);
		apiServlet.setInitOrder(1);
		context.addServlet(apiServlet, "/*");
	}

	//XXX: replace singleton pattern by dependency injection?
	private static ApiService instance;

	public static ApiService getInstance() {
		if (instance == null) {
			instance = new ApiService();
		}

		return instance;
	}
	
	Iterable<Class<?>> getResources() {
		return resources;
	}

	public void start() {
		try {
			//START RPC 
			server.start();
		} catch (Exception e) {
			//FAILED TO START RPC
		}
	}

	public void stop() {
		try {
			//STOP RPC  
			server.stop();
		} catch (Exception e) {
			//FAILED TO STOP RPC
		}
	}
}
