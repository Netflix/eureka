package com.netflix.eureka2.transport.base;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.codec.avro.AvroPipelineConfigurator;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

/**
 * @author David Liu
 */
public class SelfClosingConnectionTest {
    private AvroPipelineConfigurator codecPipeline;

    private RxServer<Object, Object> server;

    volatile MessageConnection serverBroker;
    volatile MessageConnection clientBroker;

    private final MessageConnectionMetrics clientMetrics = new MessageConnectionMetrics("client-compatibility");
    private final MessageConnectionMetrics serverMetrics = new MessageConnectionMetrics("server-compatibility");

    @Before
    public void setUp() throws Exception {
        codecPipeline = new AvroPipelineConfigurator(SampleObject.SAMPLE_OBJECT_MODEL_SET, SampleObject.rootSchema());
        setupServerAndClient(2);  // setup a client with a selfClosingConnection with lifecycle duration of 2s
    }

    private void setupServerAndClient(final long clientLifecycleDuration) throws Exception {
        final LinkedBlockingQueue<MessageConnection> queue = new LinkedBlockingQueue<>();
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                MessageConnection messageBroker = new SelfClosingConnection(
                        new BaseMessageConnection("testServer", connection, serverMetrics));
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
                        return new SelfClosingConnection(
                                new BaseMessageConnection("testClient", connection, clientMetrics),
                                clientLifecycleDuration
                        );
                    }
                });
        clientBroker = clientObservable.toBlocking().single();
        serverBroker = queue.poll(1, TimeUnit.SECONDS);
        assertNotNull(serverBroker);
    }

    @Test(timeout = 100000)
    public void testSelfTermination() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        clientBroker.lifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail();
            }

            @Override
            public void onNext(Void aVoid) {
                fail();
            }
        });

        Iterator incomingMessages = serverBroker.incoming().toBlocking().getIterator();

        Observable<Void> submitObservable = clientBroker.submit(CONTENT);
        assertTrue("Submit operation failed", submitObservable.materialize().toBlocking().first().isOnCompleted());
        assertTrue("No message received", incomingMessages.hasNext());
        assertNotNull("expected message on server side", incomingMessages.next());

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS));  // assert clientBroker completes due to selfTermination

        submitObservable = clientBroker.submit(CONTENT);
        assertFalse("Submit operation failed", submitObservable.materialize().toBlocking().first().isOnCompleted());
        assertFalse("No message received", incomingMessages.hasNext());
    }
}
