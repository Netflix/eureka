package com.netflix.eureka2.transport.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.noop.NoOpMessageConnectionMetrics;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.codec.avro.AvroPipelineConfigurator;
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
import rx.Subscriber;
import rx.functions.Func1;

import static com.netflix.eureka2.rx.RxSniffer.sniff;
import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class BaseMessageConnectionTest {

    private AvroPipelineConfigurator codecPipeline;

    private RxServer<Object, Object> server;

    volatile MessageConnection serverBroker;
    volatile MessageConnection clientBroker;

    private final MessageConnectionMetrics clientMetrics = NoOpMessageConnectionMetrics.INSTANCE;
    private final MessageConnectionMetrics serverMetrics = NoOpMessageConnectionMetrics.INSTANCE;

    @Before
    public void setUp() throws Exception {
        codecPipeline = new AvroPipelineConfigurator(SampleObject.SAMPLE_OBJECT_MODEL_SET, SampleObject.rootSchema());
        setupServerAndClient();
    }

    private void setupServerAndClient() throws Exception {
        final LinkedBlockingQueue<MessageConnection> queue = new LinkedBlockingQueue<>();
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                MessageConnection messageBroker = new BaseMessageConnection("testServer", connection, serverMetrics);
                queue.add(messageBroker);
                return messageBroker.lifecycleObservable();
            }
        }).pipelineConfigurator(codecPipeline).enableWireLogging(LogLevel.ERROR).build().start();

        int port = server.getServerPort();
        Observable<MessageConnection> clientObservable = RxNetty.newTcpClientBuilder("localhost", port)
                .pipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR)
                .build().connect()
                .map(new Func1<ObservableConnection<Object, Object>, MessageConnection>() {
                    @Override
                    public MessageConnection call(ObservableConnection<Object, Object> connection) {
                        return new BaseMessageConnection("testClient", connection, clientMetrics);
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

        assertTrue("Ack not sent", RxBlocking.isCompleted(1, TimeUnit.SECONDS, serverBroker.acknowledge()));
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

    @Test(timeout = 10000)
    public void testMultipleSubscriptionToSingleResultOnlyWriteAndFlushOnce() throws Exception {
        final SampleObject completionObj = new SampleObject(new SampleObject.Internal("STOP"));

        final List<Object> serverIncoming = new ArrayList<>();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        serverBroker.incoming().subscribe(new Subscriber<Object>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Object o) {
                if (o.equals(completionObj)) {
                    completionLatch.countDown();
                } else {
                    serverIncoming.add(o);
                }
            }
        });

        Observable<Void> ackObservable = clientBroker.submitWithAck(CONTENT);
        Observable<Void> completionObservable = clientBroker.submitWithAck(completionObj);
        ackObservable.subscribe();
        ackObservable.subscribe();
        completionObservable.subscribe();

        assertTrue(completionLatch.await(10, TimeUnit.SECONDS));

        assertEquals(1, serverIncoming.size());
        assertNotNull("expected message on server side", serverIncoming.get(0));
    }
}