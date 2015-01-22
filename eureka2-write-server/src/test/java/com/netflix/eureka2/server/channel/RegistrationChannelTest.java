package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.transport.MessageConnection;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

/**
 * server side registration channel test
 *
 * @author David Liu
 */
public class RegistrationChannelTest {

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final EvictionQueue evictionQueue = mock(EvictionQueue.class);
    private final PublishSubject<Void> transportLifeCycle = PublishSubject.create();
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final ArgumentCaptor<InstanceInfo> registryCaptor = ArgumentCaptor.forClass(InstanceInfo.class);
    private final ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);

    private InstanceInfo.Builder seed;
    private InstanceInfo register;
    private InstanceInfo update1;
    private InstanceInfo update2;

    private MessageConnection transport;

    private RegistrationChannel channel;

    @Before
    public void setUp() {
        InstanceInfo.Builder seed = new InstanceInfo.Builder().withId("id").withApp("app");

        register = seed.withStatus(InstanceInfo.Status.STARTING).build();
        update1 = seed.withStatus(InstanceInfo.Status.UP).build();
        update2 = seed.withStatus(InstanceInfo.Status.DOWN).build();

        transport = mock(MessageConnection.class);

        when(transport.lifecycleObservable()).thenReturn(transportLifeCycle);
        when(transport.incoming()).thenReturn(incomingSubject);
        when(transport.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(transport.acknowledge()).thenReturn(Observable.<Void>empty());
        when(transport.onError(any(Throwable.class))).thenReturn(Observable.<Void>empty());

        when(registry.register(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(true));
        when(registry.unregister(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(true));

        channel = spy(new RegistrationChannelImpl(registry, evictionQueue, transport, mock(RegistrationChannelMetrics.class)));
    }

    @After
    public void tearDown() {
        channel.close();
    }

    @Test
    public void testOperationsAreProcessedInOrder() throws Exception {
        incomingSubject.onNext(new Register(register));
        incomingSubject.onNext(new Register(update1));
        incomingSubject.onNext(new Register(update2));
        incomingSubject.onNext(Unregister.INSTANCE);

        verify(registry, times(3)).register(registryCaptor.capture(), any(Source.class));
        verify(registry, times(1)).unregister(registryCaptor.capture(), any(Source.class));

        List<InstanceInfo> captured = registryCaptor.getAllValues();
        assertThat(captured.size(), equalTo(4));
        assertThat(captured.get(0), sameInstanceInfoAs(register));
        assertThat(captured.get(1), sameInstanceInfoAs(update1));
        assertThat(captured.get(2), sameInstanceInfoAs(update2));
        assertThat(captured.get(3).getId(), equalTo(register.getId()));  // the unregister, just check id
    }

    @Test
    public void testRegisterOnIdleSuccess() {
        incomingSubject.onNext(new Register(register));

        verify(registry, times(1)).register(registryCaptor.capture(), any(Source.class));
        verify(transport, times(0)).onError(errorCaptor.capture());

        List<InstanceInfo> captured = registryCaptor.getAllValues();
        assertThat(captured.size(), equalTo(1));
        assertThat(captured.get(0), sameInstanceInfoAs(register));
    }

    @Test
    public void testRegisterOnIdleFail() {
        Exception exception = new Exception("msg");
        when(registry.register(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.<Boolean>error(exception));
        incomingSubject.onNext(new Register(register));

        verify(registry, times(1)).register(registryCaptor.capture(), any(Source.class));
        verify(transport, times(1)).onError(errorCaptor.capture());

        List<InstanceInfo> captured = registryCaptor.getAllValues();
        assertThat(captured.size(), equalTo(1));
        assertThat(captured.get(0), sameInstanceInfoAs(register));

        List<Exception> capturedError = errorCaptor.getAllValues();
        assertThat(capturedError.size(), equalTo(1));
        assertThat(capturedError.get(0), CoreMatchers.equalTo(exception));
    }


    /**
     * Cases:
     * 1. channel state is Registered. Call unregister on the registry, and
     *   1a. if successful, ack on the channel and close the channel regardless of the ack result
     *   1b. if unsuccessful, send error on the transport. TODO should we optimize and close the channel here?
     * 2. channel state is Idle. This is a no-op so just ack on transport and close the channel
     * 3. channel state is Closed. This is a no-op so just ack on transport and close the channel
     *
     * Note that acks can fail often if the client walks away immediately after sending an unregister
     *
     */

    @Test
    public void testUnregisterOnRegisteredSuccess() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Void aVoid) {

            }
        });

        incomingSubject.onNext(new Register(register));  // need to register first to setup the cache
        incomingSubject.onNext(Unregister.INSTANCE);

        verify(registry, times(1)).register(any(InstanceInfo.class), any(Source.class));  // for the setup register
        verify(registry, times(1)).unregister(registryCaptor.capture(), any(Source.class));
        verify(transport, times(0)).onError(errorCaptor.capture());
        verify(transport, times(2)).acknowledge();  // one for the setup register

        List<InstanceInfo> captured = registryCaptor.getAllValues();
        assertThat(captured.size(), equalTo(1));
        assertThat(captured.get(0).getId(), equalTo(register.getId()));

        // verify that the channel is now closed
        Assert.assertTrue(completionLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testUnregisterOnRegisteredFail() {
        incomingSubject.onNext(new Register(register));  // need to register first to setup the cache

        Exception exception = new Exception("msg");
        when(registry.unregister(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.<Boolean>error(exception));
        incomingSubject.onNext(Unregister.INSTANCE);

        verify(registry, times(1)).unregister(registryCaptor.capture(), any(Source.class));
        verify(transport, times(1)).onError(errorCaptor.capture());

        List<InstanceInfo> captured = registryCaptor.getAllValues();
        assertThat(captured.size(), equalTo(1));
        assertThat(captured.get(0).getId(), equalTo(register.getId()));

        List<Exception> capturedError = errorCaptor.getAllValues();
        assertThat(capturedError.size(), equalTo(1));
        assertThat(capturedError.get(0), CoreMatchers.equalTo(exception));
    }

    @Test
    public void testUnregisterOnIdleChannel() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Void aVoid) {

            }
        });

        // don't do initial register
        incomingSubject.onNext(Unregister.INSTANCE);

        verify(registry, times(0)).register(any(InstanceInfo.class), any(Source.class));
        verify(registry, times(0)).unregister(any(InstanceInfo.class), any(Source.class));
        verify(transport, times(0)).onError(errorCaptor.capture());
        verify(transport, times(1)).acknowledge();

        // verify that the channel is now closed
        Assert.assertTrue(completionLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testReturnErrorOnceClosed() throws Exception {
        channel.close();

        incomingSubject.onNext(new Register(register));
        verify(channel, times(0)).register(register);

        incomingSubject.onNext(Unregister.INSTANCE);
        verify(channel, times(0)).unregister();
    }
}