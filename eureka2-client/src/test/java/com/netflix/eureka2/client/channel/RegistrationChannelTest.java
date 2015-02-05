package com.netflix.eureka2.client.channel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.ReplaySubject;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Client side registration channel test
 *
 * @author David Liu
 */
public class RegistrationChannelTest {

    private final RegistrationChannelMetrics channelMetrics = mock(RegistrationChannelMetrics.class);

    private InstanceInfo instanceStarting;
    private InstanceInfo instanceUp;
    private InstanceInfo instanceDown;

    private MessageConnection messageConnection;
    private TransportClient transportClient;
    private RegistrationChannel channel;

    @Before
    public void setUp() {
        InstanceInfo.Builder seed = SampleInstanceInfo.DiscoveryServer.builder().withId("id").withApp("app");

        instanceStarting = seed.withStatus(InstanceInfo.Status.STARTING).build();
        instanceUp = seed.withStatus(InstanceInfo.Status.UP).build();
        instanceDown = seed.withStatus(InstanceInfo.Status.DOWN).build();

        final ReplaySubject<Void> connectionLifecycle = ReplaySubject.create();
        messageConnection = mock(MessageConnection.class);
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.lifecycleObservable()).thenReturn(connectionLifecycle);

        transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        channel = new RegistrationChannelImpl(transportClient, channelMetrics);
    }

    @After
    public void tearDown() {
        channel.close();
    }

    @Test(timeout = 60000)
    public void testOperationsAreSubmittedInOrder() {
        channel.register(instanceStarting).subscribe();
        channel.register(instanceUp).subscribe();
        channel.register(instanceDown).subscribe();
        channel.unregister().subscribe();

        InOrder inOrder = inOrder(messageConnection);
        inOrder.verify(messageConnection).submitWithAck(new Register(instanceStarting));
        inOrder.verify(messageConnection).submitWithAck(new Register(instanceUp));
        inOrder.verify(messageConnection).submitWithAck(new Register(instanceDown));
        inOrder.verify(messageConnection).submitWithAck(Unregister.INSTANCE);
    }

    @Test(timeout = 60000)
    public void testReturnErrorForRegisterOnceClosed() throws Exception {
        channel.close();

        final CountDownLatch onErrorLatch = new CountDownLatch(1);
        channel.register(instanceStarting).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                onErrorLatch.countDown();
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });

        assertTrue(onErrorLatch.await(10, TimeUnit.SECONDS));
    }


    @Test(timeout = 60000)
    public void testReturnErrorForUnregisterOnceClosed() throws Exception {
        channel.close();

        final CountDownLatch onErrorLatch = new CountDownLatch(1);
        channel.unregister().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                onErrorLatch.countDown();
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });

        assertTrue(onErrorLatch.await(10, TimeUnit.SECONDS));
    }

    @Test(timeout = 60000)
    public void testMetricsWithUnregister() throws Exception {
        // Subscriber to interest subscription, to open the channel
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.register(instanceStarting).subscribe(testSubscriber);

        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Registered);

        // Now unregister
        channel.unregister().subscribe();
        verify(channelMetrics, times(1)).decrementStateCounter(STATE.Registered);
        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Closed);
        reset(channelMetrics);

        // Shutdown channel
        channel.close();
        verify(channelMetrics, times(0)).decrementStateCounter(STATE.Registered);
        verify(channelMetrics, times(0)).incrementStateCounter(STATE.Closed);
    }

    @Test(timeout = 60000)
    public void testMetricsWithClosedWhileStillRegistered() throws Exception {
        // Subscriber to interest subscription, to open the channel
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.register(instanceStarting).subscribe(testSubscriber);

        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Registered);

        // Shutdown channel
        channel.close();
        verify(channelMetrics, times(1)).decrementStateCounter(STATE.Registered);
        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Closed);
    }
}
