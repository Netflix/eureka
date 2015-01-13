package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import rx.Observable;
import rx.Subscriber;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Client side registration channel test
 *
 * @author David Liu
 */
public class RegistrationChannelTest {

    private InstanceInfo.Builder seed;
    private InstanceInfo register;
    private InstanceInfo update1;
    private InstanceInfo update2;

    private MessageConnection messageConnection;
    private TransportClient transportClient;
    private RegistrationChannel channel;

    @Before
    public void setUp() {
        InstanceInfo.Builder seed = new InstanceInfo.Builder().withId("id").withApp("app");

        register = seed.withStatus(InstanceInfo.Status.STARTING).build();
        update1 = seed.withStatus(InstanceInfo.Status.UP).build();
        update2 = seed.withStatus(InstanceInfo.Status.DOWN).build();

        messageConnection = mock(MessageConnection.class);
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());

        transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));
    }

    @After
    public void tearDown() {
        channel.close();
    }

    @Test
    public void testOperationsAreSubmittedInOrder() {
        channel.register(register).subscribe();
        channel.register(update1).subscribe();
        channel.register(update2).subscribe();
        channel.unregister().subscribe();

        InOrder inOrder = inOrder(messageConnection);
        inOrder.verify(messageConnection).submitWithAck(new Register(register));
        inOrder.verify(messageConnection).submitWithAck(new Register(update1));
        inOrder.verify(messageConnection).submitWithAck(new Register(update2));
        inOrder.verify(messageConnection).submitWithAck(Unregister.INSTANCE);
    }

    @Test
    public void testReturnErrorForRegisterOnceClosed() throws Exception {
        channel.close();

        final CountDownLatch onErrorLatch = new CountDownLatch(1);
        channel.register(register).subscribe(new Subscriber<Void>() {
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


    @Test
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
}
