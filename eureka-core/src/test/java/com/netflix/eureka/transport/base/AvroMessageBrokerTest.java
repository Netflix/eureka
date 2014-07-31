package com.netflix.eureka.transport.base;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import com.netflix.eureka.transport.utils.BrokerUtils.BrokerPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Observable;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AvroMessageBrokerTest {

    private static final UserContent CONTENT = new UserContent(new SampleUserObject("stringValue", 123));

    MessageBrokerServer server;
    MessageBroker serverBroker;
    MessageBroker clientBroker;

    @Before
    public void setUp() throws Exception {
        server = new TcpMessageBrokerBuilder(new InetSocketAddress(0))
                .withCodecPipeline(new AvroPipelineConfigurator(SampleUserObject.class))
                .buildServer().start();
        Observable<MessageBroker> serverObservable = server.clientConnections();
        int port = server.getServerPort();

        Observable<MessageBroker> clientObservable = new TcpMessageBrokerBuilder(new InetSocketAddress("localhost", port))
                .withCodecPipeline(new AvroPipelineConfigurator(SampleUserObject.class))
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

    @Test
    public void testSubmitUserContent() throws Exception {
        Iterator<Message> incomingMessages = serverBroker.incoming().toBlocking().getIterator();

        clientBroker.submit(CONTENT);

        assertTrue("No message received", incomingMessages.hasNext());
        assertNotNull("expected message on server side", incomingMessages.next());
    }

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