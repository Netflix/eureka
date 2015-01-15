package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class AbstractHandlerChannelTest {

    private final MessageConnection transport = mock(MessageConnection.class);
    private final Exception transportOnErrorReturn = new Exception();

    private AbstractHandlerChannel channel;

    @Before
    public void setUp() {
        when(transport.submit(anyObject())).thenReturn(Observable.<Void>empty());
        // this is current BaseMessageConnection behaviour
        when(transport.onError(any(Throwable.class))).thenReturn(Observable.<Void>error(transportOnErrorReturn));
        channel = spy(new TestHandlerChannel(transport));
    }

    @Test
    public void testCloseChannelOnSendError() throws Exception {
        when(transport.submit(anyObject())).thenReturn(Observable.<Void>error(new Exception("msg")));

        final CountDownLatch onCompletedLatch = new CountDownLatch(1);
        channel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                onCompletedLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });

        channel.sendOnTransport("My Message");

        verify(channel, times(1)).close();
        Assert.assertTrue(onCompletedLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testCloseChannelWhenSendingErrorOnTransportSuccessfully() throws Exception {

        final CountDownLatch onCompletedLatch = new CountDownLatch(1);
        channel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                onCompletedLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });

        channel.sendErrorOnTransport(new Exception("some error"));

        verify(channel, times(1)).close();
        Assert.assertTrue(onCompletedLatch.await(10, TimeUnit.SECONDS));
    }


    public class TestHandlerChannel extends AbstractHandlerChannel<Enum> {
        protected TestHandlerChannel(MessageConnection transport) {
            super(null, transport, mock(SourcedEurekaRegistry.class));
        }
    }
}
