package com.netflix.eureka.transport.avro;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.utils.BrokerUtils.BrokerPair;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import rx.Notification;
import rx.Observable;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AvroMessageBrokerTest {

    private static final UserContent CONTENT = new UserContent(new SampleUserObject("stringValue", 123));

    AvroMessageBrokerServer server;
    MessageBroker serverBroker;
    MessageBroker clientBroker;

    @Before
    public void setUp() throws Exception {
        server = new AvroMessageBrokerBuilder(new InetSocketAddress(0))
                .withTypes(SampleUserObject.class)
                .buildServer().start();
        Observable<MessageBroker> serverObservable = server.clientConnections();
        int port = server.server.getServerPort();

        Observable<MessageBroker> clientObservable = new AvroMessageBrokerBuilder(new InetSocketAddress("localhost", port))
                .withTypes(SampleUserObject.class)
                .buildClient();

        BrokerPair brokerPair = new BrokerPair(serverObservable, clientObservable);
        serverBroker = brokerPair.getServerBroker();
        clientBroker = brokerPair.getClientBroker();
    }

    @After
    public void tearDown() throws Exception {
        clientBroker.shutdown();
        serverBroker.shutdown();
        server.shutdown();
    }

    @Ignore
    @Test
    public void testSubmitUserContent() throws Exception {
        Iterator<Message> incomingMessages = serverBroker.incoming().toBlocking().getIterator();

        clientBroker.submit(CONTENT);

        assertTrue("No message received", incomingMessages.hasNext());
        assertNotNull("expected message on server side", incomingMessages.next());
    }

    @Ignore
    @Test
    public void testSubmitUserContentWithAck() throws Exception {
        Iterator<Message> serverIncoming = serverBroker.incoming().toBlocking().getIterator();

        Observable<Acknowledgement> acknowledgementObservable = clientBroker.submitWithAck(CONTENT);
        Iterator<Acknowledgement> ackIterator = acknowledgementObservable.toBlocking().getIterator();

        assertTrue("No message received", serverIncoming.hasNext());
        UserContentWithAck receivedMessage = (UserContentWithAck) serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        serverBroker.acknowledge(receivedMessage);

        assertTrue("Ack not received", ackIterator.hasNext());
        assertTrue("Expected Acknowledgement instance", ackIterator.next() instanceof Acknowledgement);
    }

    @Ignore
    @Test
    public void testAckTimeout() throws Exception {
        Iterator<Message> serverIncoming = serverBroker.incoming().toBlocking().getIterator();

        Observable<Acknowledgement> acknowledgementObservable = clientBroker.submitWithAck(CONTENT, 1);
        Iterator<Notification<Acknowledgement>> ackIterator = acknowledgementObservable.materialize().toBlocking().getIterator();

        assertTrue("No message received", serverIncoming.hasNext());
        UserContentWithAck receivedMessage = (UserContentWithAck) serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        // Client side timeout
        assertTrue("Ack not received", ackIterator.hasNext());
        assertTrue("Expected Acknowledgement instance", ackIterator.next().getThrowable() instanceof TimeoutException);

        // Server side timeout
        //assertFalse("Acknowledgement should be rejected", serverBroker.acknowledge(receivedMessage));
    }
}