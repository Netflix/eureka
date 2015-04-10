package com.netflix.eureka2.transport.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.transport.EurekaPipelineConfigurator;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.codec.AbstractEurekaCodec;
import com.netflix.eureka2.transport.codec.DynamicEurekaCodec;
import com.netflix.eureka2.transport.codec.avro.AvroCodec;
import com.netflix.eureka2.transport.codec.avro.SchemaReflectData;
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
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static com.netflix.eureka2.transport.base.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Tomasz Bak
 */
public class BaseMessageConnectionTest {

    private EurekaPipelineConfigurator codecPipeline;

    private RxServer<Object, Object> server;

    volatile MessageConnection serverBroker;
    volatile MessageConnection clientBroker;

    private final MessageConnectionMetrics clientMetrics = mock(MessageConnectionMetrics.class);
    private final MessageConnectionMetrics serverMetrics = mock(MessageConnectionMetrics.class);

    @Before
    public void setUp() throws Exception {
        Func1<Codec, AbstractEurekaCodec> sampleObjectFunc = new Func1<Codec, AbstractEurekaCodec>() {
            @Override
            public AbstractEurekaCodec call(Codec codec) {
                Map<Byte, AbstractEurekaCodec> map = new HashMap<>();
                map.put(Codec.Avro.getVersion(), new AvroCodec(SampleObject.SAMPLE_OBJECT_MODEL_SET, SampleObject.rootSchema(), new SchemaReflectData(SampleObject.rootSchema())));
                return new DynamicEurekaCodec(SAMPLE_OBJECT_MODEL_SET, Collections.unmodifiableMap(map), codec.getVersion());
            }
        };
        codecPipeline = new EurekaPipelineConfigurator(sampleObjectFunc, Codec.Avro);
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

        Observable<Void> ackObservable = clientBroker.submitWithAck(CONTENT);
        ackObservable.subscribe();

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

    @Test(timeout = 60000)
    public void testClosesLocalConnectionOnRemoteServerDisconnectWithError() throws Exception {
        // Connect to client  input stream and lifecycle which we will examine after server disconnect.
        TestSubscriber<Object> testSubscriber = new TestSubscriber<>();
        clientBroker.incoming().subscribe(testSubscriber);

        TestSubscriber<Void> lifecycleSubscriber = new TestSubscriber<>();
        clientBroker.lifecycleObservable().subscribe(lifecycleSubscriber);

        // Close connection on the remote endpoint
        serverBroker.shutdown();

        // Verify that client side detected the disconnect, and local shutdown was triggered.
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();

        lifecycleSubscriber.assertTerminalEvent();
        assertThat(lifecycleSubscriber.getOnErrorEvents().size(), is(1));
    }

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        // Connection was already setup in setUp method
        verify(clientMetrics, times(1)).incrementConnectionCounter();
        verify(serverMetrics, times(1)).incrementConnectionCounter();

        // Send message client -> server
        ExtTestSubscriber<Object> testSubscriber = new ExtTestSubscriber<>();
        serverBroker.incoming().subscribe(testSubscriber);
        clientBroker.submit(CONTENT).subscribe();

        assertThat(testSubscriber.takeNextOrWait(), is(equalTo((Object) CONTENT)));

        verify(clientMetrics, times(1)).incrementOutgoingMessageCounter(CONTENT.getClass(), 1);
        verify(serverMetrics, times(1)).incrementIncomingMessageCounter(CONTENT.getClass(), 1);

        // Send message server -> client
        testSubscriber = new ExtTestSubscriber<>();
        clientBroker.incoming().subscribe(testSubscriber);
        serverBroker.submit(CONTENT).subscribe();

        assertThat(testSubscriber.takeNextOrWait(), is(equalTo((Object) CONTENT)));

        verify(serverMetrics, times(1)).incrementOutgoingMessageCounter(CONTENT.getClass(), 1);
        verify(clientMetrics, times(1)).incrementIncomingMessageCounter(CONTENT.getClass(), 1);

        // Close connection
        clientBroker.shutdown();
        TestSubscriber<Void> lifecycleSubscriber = new TestSubscriber<>();
        serverBroker.lifecycleObservable().subscribe(lifecycleSubscriber);
        lifecycleSubscriber.awaitTerminalEvent();

        verify(clientMetrics, times(1)).decrementConnectionCounter();
        verify(serverMetrics, times(1)).decrementConnectionCounter();

        verify(clientMetrics, times(1)).connectionDuration(anyLong());
        verify(serverMetrics, times(1)).connectionDuration(anyLong());
    }
}