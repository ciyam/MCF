package api;

import io.swagger.v3.jaxrs2.integration.OpenApiServlet;
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
    private Server server;
    private Set<Class<?>> resources;

    public ApiService()
    {
        // resources to register
        resources = new HashSet<Class<?>>();
        resources.add(BlocksResource.class);
        resources.add(OpenApiResource.class); // swagger
        ResourceConfig config = new ResourceConfig(resources);     

        // create RPC server
        this.server = new Server(Settings.getInstance().getRpcPort());
        
        // whitelist
        InetAccessHandler accessHandler = new InetAccessHandler();
        for(String pattern : Settings.getInstance().getRpcAllowed())
                accessHandler.include(pattern);
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
    
    Iterable<Class<?>> getResources()
    {
        return resources;
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
