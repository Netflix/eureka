package com.netflix.eureka.transport.base;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import com.netflix.eureka.transport.utils.BrokerUtils.BrokerPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Observable;

import static com.netflix.eureka.rx.RxSniffer.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBrokerTest {

    private static final SampleUserObject CONTENT = new SampleUserObject("stringValue", 123);

    MessageBrokerServer<SampleUserObject, SampleUserObject> server;
    MessageBroker<SampleUserObject, SampleUserObject> serverBroker;
    MessageBroker<SampleUserObject, SampleUserObject> clientBroker;

    @Before
    public void setUp() throws Exception {
        AvroPipelineConfigurator<SampleUserObject, SampleUserObject> codecPipeline =
                new AvroPipelineConfigurator<SampleUserObject, SampleUserObject>(SampleUserObject.TRANSPORT_MODEL);

        server = new TcpMessageBrokerBuilder<SampleUserObject, SampleUserObject>(new InetSocketAddress(0))
                .withCodecPiepline(codecPipeline)
                .buildServer().start();
        Observable<MessageBroker<SampleUserObject, SampleUserObject>> serverObservable = server.clientConnections();
        int port = server.getServerPort();

        Observable<MessageBroker<SampleUserObject, SampleUserObject>> clientObservable =
                new TcpMessageBrokerBuilder<SampleUserObject, SampleUserObject>(new InetSocketAddress("localhost", port))
                        .withCodecPiepline(codecPipeline)
                        .buildClient();

        BrokerPair<SampleUserObject, SampleUserObject> brokerPair =
                new BrokerPair<SampleUserObject, SampleUserObject>(serverObservable, clientObservable);
        serverBroker = brokerPair.getServerBroker();
        clientBroker = brokerPair.getClientBroker();
    }

    @After
    public void tearDown() throws Exception {
        if (clientBroker != null) {
            clientBroker.shutdown();
        }
        if (serverBroker != null) {
            serverBroker.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testSubmitUserContent() throws Exception {
        Iterator<SampleUserObject> incomingMessages = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> submitObservable = clientBroker.submit(CONTENT);
        assertTrue("Submit operation failed", submitObservable.materialize().toBlocking().first().isOnCompleted());

        assertTrue("No message received", incomingMessages.hasNext());
        assertNotNull("expected message on server side", incomingMessages.next());
    }

    @Test
    public void testSubmitUserContentWithAck() throws Exception {
        Iterator<SampleUserObject> serverIncoming = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> ackObservable = sniff("ack", clientBroker.submitWithAck(CONTENT));
        Iterator<Notification<Void>> ackIterator = ackObservable.materialize().toBlocking().getIterator();

        assertTrue("No message received", serverIncoming.hasNext());
        SampleUserObject receivedMessage = serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        serverBroker.acknowledge(receivedMessage);

        assertTrue("Ack not received", ackIterator.hasNext());
        assertTrue("Expected completed ack observable", ackIterator.next().isOnCompleted());
    }

    @Test
    public void testAckTimeout() throws Exception {
        Iterator<SampleUserObject> serverIncoming = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> acknowledgementObservable = clientBroker.submitWithAck(CONTENT, 1);
        Iterator<Notification<Void>> ackIterator = acknowledgementObservable.materialize().toBlocking().getIterator();

        assertTrue("No message received", serverIncoming.hasNext());
        SampleUserObject receivedMessage = serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        // Client side timeout
        assertTrue("Ack not received", ackIterator.hasNext());
        assertTrue("Expected Acknowledgement instance", ackIterator.next().getThrowable() instanceof TimeoutException);
    }
}