package com.netflix.eureka2.server.channel;

import java.io.IOException;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * server side registration channel test
 *
 * @author David Liu
 */
public class RegistrationChannelImplTest {

    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor = mock(EurekaRegistrationProcessor.class);
    private final EvictionQueue evictionQueue = mock(EvictionQueue.class);
    private final PublishSubject<Void> transportLifeCycle = PublishSubject.create();
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();

    private InstanceInfo registerInfo;
    private InstanceInfo update1Info;

    private MessageConnection transport;

    private RegistrationChannel channel;
    private final ExtTestSubscriber<InstanceInfo> registrationSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() {
        InstanceInfo.Builder seed = new InstanceInfo.Builder().withId("id").withApp("app");

        registerInfo = seed.withStatus(InstanceInfo.Status.STARTING).build();
        update1Info = seed.withStatus(InstanceInfo.Status.UP).build();

        transport = mock(MessageConnection.class);

        when(transport.lifecycleObservable()).thenReturn(transportLifeCycle);
        when(transport.incoming()).thenReturn(incomingSubject);
        when(transport.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(transport.acknowledge()).thenReturn(Observable.<Void>empty());
        when(transport.onError(any(Throwable.class))).thenReturn(Observable.<Void>empty());

        when(registrationProcessor.register(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(true));
        when(registrationProcessor.unregister(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(true));

        channel = spy(new RegistrationChannelImpl(registrationProcessor, evictionQueue, transport, mock(RegistrationChannelMetrics.class)));
    }

    @After
    public void tearDown() {
        channel.close();
    }

    @Test
    public void testRegistrationLifecycle() throws Exception {
        mockRegistrationProcessorWithSubject();

        // First registration
        incomingSubject.onNext(new Register(registerInfo));
        assertThat(registrationSubscriber.takeNext(), is(equalTo(registerInfo)));

        // Status update
        incomingSubject.onNext(new Register(update1Info));
        assertThat(registrationSubscriber.takeNext(), is(equalTo(update1Info)));

        // Now unregister
        incomingSubject.onNext(new Unregister());
        registrationSubscriber.assertOnCompleted();
    }

    @Test
    public void testChannelDisconnectWithoutUnregister() throws Exception {
        mockRegistrationProcessorWithSubject();

        // First registration
        incomingSubject.onNext(new Register(registerInfo));
        assertThat(registrationSubscriber.takeNext(), is(equalTo(registerInfo)));

        // Simulated transport error
        transportLifeCycle.onError(new IOException("simulated transport error"));
        registrationSubscriber.assertOnError();
    }

    @Test
    public void testFailedRegistrationInRegistry() {
        when(registrationProcessor.register(anyString(), any(Observable.class), any(Source.class))).thenReturn(
                Observable.<Void>error(new Exception("simulated registry error"))
        );
        incomingSubject.onNext(new Register(registerInfo));

        verify(transport, times(1)).shutdown();
    }

    @Test(timeout = 60000)
    public void testUnregisterOnIdleChannel() throws Exception {
        mockRegistrationProcessorWithSubject();

        // First registration
        incomingSubject.onNext(new Unregister());
        assertThat(registrationSubscriber.takeNext(), is(nullValue()));

        ExtTestSubscriber<Void> channelLifecycleSubscriber = new ExtTestSubscriber<>();
        channel.asLifecycleObservable().subscribe(channelLifecycleSubscriber);

        channelLifecycleSubscriber.assertOnCompleted();
    }

    @Test(timeout = 60000)
    public void testReturnErrorOnceClosed() throws Exception {
        channel.close();

        incomingSubject.onNext(new Register(registerInfo));
        verify(channel, times(0)).register(registerInfo);

        incomingSubject.onNext(Unregister.INSTANCE);
        verify(channel, times(0)).unregister();
    }

    private void mockRegistrationProcessorWithSubject() {
        when(registrationProcessor.register(anyString(), any(Observable.class), any(Source.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Observable<InstanceInfo> registrationObservable = (Observable<InstanceInfo>) invocation.getArguments()[1];
                registrationObservable.subscribe(registrationSubscriber);
                return Observable.empty();
            }
        });
    }
}