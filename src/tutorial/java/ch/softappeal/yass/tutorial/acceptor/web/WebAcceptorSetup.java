package ch.softappeal.yass.tutorial.acceptor.web;

import ch.softappeal.yass.transport.TransportSetup;
import ch.softappeal.yass.transport.ws.AsyncWsConnection;
import ch.softappeal.yass.transport.ws.WsConnection;
import ch.softappeal.yass.transport.ws.WsEndpoint;
import ch.softappeal.yass.tutorial.acceptor.AcceptorSession;
import ch.softappeal.yass.tutorial.contract.Config;
import ch.softappeal.yass.util.Exceptions;
import ch.softappeal.yass.util.NamedThreadFactory;

import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class WebAcceptorSetup {

    public static final String HOST = "0.0.0.0";
    public static final int PORT = 9090;
    public static final String PATH = "/tutorial";
    protected static final String XHR_PATH = "/xhr";
    protected static final String WEB_PATH = ".";

    public static final Executor DISPATCH_EXECUTOR = Executors.newCachedThreadPool(new NamedThreadFactory("dispatchExecutor", Exceptions.STD_ERR));

    private static final WsConnection.Factory CONNECTION_FACTORY = AsyncWsConnection.factory(1_000);

    private static final TransportSetup TRANSPORT_SETUP = TransportSetup.ofContractSerializer(
        Config.SERIALIZER,
        connection -> new AcceptorSession(connection, DISPATCH_EXECUTOR)
    );

    public static final class Endpoint extends WsEndpoint {
        @Override protected WsConnection createConnection(final Session session) throws Exception {
            return WsConnection.create(CONNECTION_FACTORY, TRANSPORT_SETUP, session);
        }
    }

    protected static ServerEndpointConfig endpointConfig(final ServerEndpointConfig.Configurator configurator) {
        return ServerEndpointConfig.Builder.create(Endpoint.class, PATH).configurator(configurator).build();
    }

}
