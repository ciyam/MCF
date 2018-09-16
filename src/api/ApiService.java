package api;

//import io.swagger.jaxrs.config.DefaultJaxrsConfig;

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

    public ApiService()
    {
        // resources to register
        Set<Class<?>> s = new HashSet<Class<?>>();
        s.add(BlocksResource.class);
        ResourceConfig config = new ResourceConfig(s);     

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
        context.addServlet(apiServlet, "/api/*");

        /*
        // Setup Swagger servlet
        ServletHolder swaggerServlet = context.addServlet(DefaultJaxrsConfig.class, "/swagger-core");
        swaggerServlet.setInitOrder(2);
        swaggerServlet.setInitParameter("api.version", "1.0.0");
        */

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
