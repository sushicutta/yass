package ch.softappeal.yass.tutorial.initiator.socket;

import ch.softappeal.yass.transport.TransportSetup;
import ch.softappeal.yass.transport.socket.SimpleSocketConnector;
import ch.softappeal.yass.transport.socket.SocketTransport;
import ch.softappeal.yass.transport.socket.SyncSocketConnection;
import ch.softappeal.yass.tutorial.acceptor.socket.SocketAcceptor;
import ch.softappeal.yass.tutorial.contract.Config;
import ch.softappeal.yass.tutorial.initiator.InitiatorReconnector;
import ch.softappeal.yass.tutorial.initiator.InitiatorSession;
import ch.softappeal.yass.util.Exceptions;
import ch.softappeal.yass.util.NamedThreadFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ReconnectingSocketInitiator {

    public static void main(final String... args) throws InterruptedException {
        final Executor executor = Executors.newCachedThreadPool(new NamedThreadFactory("executor", Exceptions.STD_ERR));
        final InitiatorReconnector reconnector = new InitiatorReconnector();
        reconnector.start(
            executor,
            10,
            connection -> new InitiatorSession(connection, executor),
            sessionFactory -> SocketTransport.connect(
                executor,
                SyncSocketConnection.FACTORY,
                TransportSetup.ofContractSerializer(Config.CONTRACT_SERIALIZER, sessionFactory),
                new SimpleSocketConnector(SocketAcceptor.ADDRESS)
            )
        );
        System.out.println("started");
        while (true) {
            TimeUnit.SECONDS.sleep(1L);
            if (reconnector.connected()) {
                try {
                    System.out.println(reconnector.echoService.echo("knock"));
                } catch (final Exception e) {
                    System.out.println("race condition: " + e.toString());
                }
            } else {
                System.out.println("not connected");
            }
        }
    }

}
