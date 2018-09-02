package api;

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

	public Server server;
	
	public ApiService()
	{
		//CREATE CONFIG
		Set<Class<?>> s = new HashSet<Class<?>>();
        s.add(BlocksResource.class);
		
		ResourceConfig config = new ResourceConfig(s);
		
        //CREATE CONTAINER
        ServletContainer container = new ServletContainer(config);
		
		//CREATE CONTEXT
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(container),"/*");
        
        //CREATE WHITELIST
        InetAccessHandler accessHandler = new InetAccessHandler();
        for(String pattern : Settings.getInstance().getRpcAllowed())
        	accessHandler.include(pattern);
        accessHandler.setHandler(context);
        
        //CREATE RPC SERVER
      	this.server = new Server(Settings.getInstance().getRpcPort());
      	this.server.setHandler(accessHandler);
	}
	
	public void start()
	{
		try
        {
        	//START RPC 
			server.start();
		} 
        catch (Exception e) 
		{
        	//FAILED TO START RPC
		}
	}
	
	public void stop()
	{
		try 
        {
			//STOP RPC  
			server.stop();
		} 
        catch (Exception e) 
		{
        	//FAILED TO STOP RPC
		}
	}
}
