package com.netflix.eureka2.transport.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka2.codec.SampleObject;
import com.netflix.eureka2.codec.jackson.JacksonEurekaCodecFactory;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.protocol.StdAcknowledgement;
import com.netflix.eureka2.protocol.StdProtocolModel;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.internal.rx.RxBlocking;
import com.netflix.eureka2.spi.protocol.Acknowledgement;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.transport.EurekaPipelineConfigurator;
import com.netflix.eureka2.transport.codec.EurekaCodecWrapperFactory;
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
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.codec.SampleObject.CONTENT;
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

    static {
        StdModelsInjector.injectStdModels();
    }

    private EurekaPipelineConfigurator codecPipeline;

    private RxServer<Object, Object> server;

    volatile EurekaConnection serverBroker;
    volatile EurekaConnection clientBroker;

    private final MessageConnectionMetrics clientMetrics = mock(MessageConnectionMetrics.class);
    private final MessageConnectionMetrics serverMetrics = mock(MessageConnectionMetrics.class);

    @Before
    public void setUp() throws Exception {
        codecPipeline = new EurekaPipelineConfigurator(new EurekaCodecWrapperFactory(new JacksonEurekaCodecFactory(SampleObject.class, StdAcknowledgement.class)));
        setupServerAndClient();
    }

    private void setupServerAndClient() throws Exception {
        final LinkedBlockingQueue<EurekaConnection> queue = new LinkedBlockingQueue<>();
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                EurekaConnection messageBroker = new BaseMessageConnection("testServer", connection, serverMetrics);
                queue.add(messageBroker);
                return messageBroker.lifecycleObservable().materialize().ignoreElements().cast(Void.class);
            }
        }).pipelineConfigurator(codecPipeline).enableWireLogging(LogLevel.ERROR).build().start();

        int port = server.getServerPort();
        Observable<EurekaConnection> clientObservable = RxNetty.newTcpClientBuilder("localhost", port)
                .pipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR)
                .build().connect()
                .map(new Func1<ObservableConnection<Object, Object>, EurekaConnection>() {
                    @Override
                    public EurekaConnection call(ObservableConnection<Object, Object> connection) {
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

    @Test(timeout = 10000)
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
        final List<Object> serverIncoming = new ArrayList<>();
        serverBroker.incoming().doOnNext(new Action1<Object>() {
            @Override
            public void call(Object o) {
                serverIncoming.add(o);
                serverBroker.acknowledge();
            }
        }).subscribe();

        ExtTestSubscriber<Void> ackSubscriber1 = new ExtTestSubscriber<>();
        ExtTestSubscriber<Void> ackSubscriber2 = new ExtTestSubscriber<>();
        Observable<Void> ackObservable = clientBroker.submitWithAck(CONTENT);
        ackObservable.subscribe(ackSubscriber1);
        ackObservable.subscribe(ackSubscriber2);

        ackSubscriber1.assertOnCompleted(1, TimeUnit.SECONDS);
        ackSubscriber2.assertOnCompleted(1, TimeUnit.SECONDS);

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