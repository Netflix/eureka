package com.netflix.eureka.transport.base;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.base.SampleObject.Internal;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;

import static com.netflix.eureka.rx.RxSniffer.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBrokerTest {

    private static final SampleObject CONTENT = new SampleObject(new Internal("abc"));

    private RxServer<Object, Object> server;

    volatile MessageBroker serverBroker;
    volatile MessageBroker clientBroker;

    @Before
    public void setUp() throws Exception {
        AvroPipelineConfigurator codecPipeline =
                new AvroPipelineConfigurator(SampleObject.SAMPLE_OBJECT_MODEL_SET, SampleObject.rootSchema());

        final LinkedBlockingQueue<MessageBroker> queue = new LinkedBlockingQueue<>();
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                BaseMessageBroker messageBroker = new BaseMessageBroker(connection);
                queue.add(messageBroker);
                return messageBroker.lifecycleObservable();
            }
        }).pipelineConfigurator(codecPipeline).enableWireLogging(LogLevel.ERROR).build().start();

        int port = server.getServerPort();
        Observable<MessageBroker> clientObservable = RxNetty.newTcpClientBuilder("localhost", port)
                .pipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR)
                .build().connect()
                .map(new Func1<ObservableConnection<Object, Object>, MessageBroker>() {
                    @Override
                    public MessageBroker call(ObservableConnection<Object, Object> connection) {
                        return new BaseMessageBroker(connection);
                    }
                });
        clientBroker = clientObservable.toBlocking().single();
        serverBroker = queue.poll(1, TimeUnit.SECONDS);
        assertNotNull(serverBroker);
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

    @Test(timeout = 1000)
    public void testSubmitUserContent() throws Exception {
        Iterator incomingMessages = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> submitObservable = clientBroker.submit(CONTENT);
        assertTrue("Submit operation failed", submitObservable.materialize().toBlocking().first().isOnCompleted());

        assertTrue("No message received", incomingMessages.hasNext());
        assertNotNull("expected message on server side", incomingMessages.next());
    }

    @Test(timeout = 1000)
    public void testSubmitUserContentWithAck() throws Exception {
        Iterator serverIncoming = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> ackObservable = sniff("ack", clientBroker.submitWithAck(CONTENT));

        assertTrue("No message received", serverIncoming.hasNext());
        SampleObject receivedMessage = (SampleObject) serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        assertTrue("Ack not sent", RxBlocking.isCompleted(1, TimeUnit.SECONDS, serverBroker.acknowledge(receivedMessage)));
        assertTrue("Expected completed ack observable", RxBlocking.isCompleted(1, TimeUnit.SECONDS, ackObservable));
    }

    @Test(timeout = 10000)
    public void testAckTimeout() throws Exception {
        Iterator<Object> serverIncoming = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, serverBroker.incoming());

        Observable<Void> ackObservable = clientBroker.submitWithAck(CONTENT, 1);
        Iterator<Notification<Void>> ackIterator = ackObservable.materialize().toBlocking().getIterator();

        assertTrue("No message received", serverIncoming.hasNext());
        SampleObject receivedMessage = (SampleObject) serverIncoming.next();
        assertNotNull("expected message on server side", receivedMessage);

        // Client side timeout
        assertTrue("Ack not received", ackIterator.hasNext());
        assertTrue("Expected Acknowledgement instance", ackIterator.next().getThrowable() instanceof TimeoutException);
    }
}