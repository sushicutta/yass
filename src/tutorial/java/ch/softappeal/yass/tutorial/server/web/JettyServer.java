package ch.softappeal.yass.tutorial.server.web;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.BasicServerEndpointConfigurator;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public final class JettyServer extends WsServerSetup {

    public static void main(final String... args) throws Exception {
        final Server server = new Server();
        final ServerConnector serverConnector = new ServerConnector(server);
        serverConnector.setHost(HOST);
        serverConnector.setPort(PORT);
        server.addConnector(serverConnector);
        final ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        contextHandler.setContextPath("/");
        contextHandler.addServlet(new ServletHolder(new XhrServlet()), XHR_PATH);
        contextHandler.addServlet(new ServletHolder(new HelloServlet()), HELLO_PATH);
        final ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(WEB_PATH);
        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] {resourceHandler, contextHandler});
        server.setHandler(handlers);
        WebSocketServerContainerInitializer.configureContext(contextHandler).addEndpoint(endpointConfig(new BasicServerEndpointConfigurator()));
        server.start();
        System.out.println("started");
    }

}
