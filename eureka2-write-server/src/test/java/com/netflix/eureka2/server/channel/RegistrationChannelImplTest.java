package com.netflix.eureka2.server.channel;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.testkit.junit.EurekaMatchers;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * server side registration channel test
 *
 * @author David Liu
 */
public class RegistrationChannelImplTest {

    private final PublishSubject<Void> transportLifeCycle = PublishSubject.create();
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> dataSubscriber = new ExtTestSubscriber<>();
    private final TestSubscriber<Void> lifecycleSubscriber = new TestSubscriber<>();

    private InstanceInfo registerInfo;
    private InstanceInfo update1Info;

    private MessageConnection transport;
    private RegistrationChannelImpl channel;

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

        EurekaRegistrationProcessor<InstanceInfo> registrationProcessor = mock(EurekaRegistrationProcessor.class);
        when(registrationProcessor.connect(anyString(), any(Source.class), any(Observable.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                Observable<ChangeNotification<InstanceInfo>> dataStream = (Observable<ChangeNotification<InstanceInfo>>) invocation.getArguments()[2];
                dataStream.subscribe(dataSubscriber);
                return Observable.never();
            }
        });
        channel = spy(new RegistrationChannelImpl(registrationProcessor, transport, mock(RegistrationChannelMetrics.class)));
    }

    @After
    public void tearDown() {
        channel.close();
    }

    @Test
    public void testRegistrationLifecycle() throws Exception {
        // First registration
        incomingSubject.onNext(new Register(registerInfo));
        assertThat(dataSubscriber.takeNext(1, TimeUnit.SECONDS), EurekaMatchers.addChangeNotificationOf(registerInfo));

        // Status update
        incomingSubject.onNext(new Register(update1Info));
        assertThat(dataSubscriber.takeNext(1, TimeUnit.SECONDS), EurekaMatchers.addChangeNotificationOf(update1Info));

        // Now unregister
        incomingSubject.onNext(new Unregister());
        assertThat(dataSubscriber.takeNext(1, TimeUnit.SECONDS), EurekaMatchers.deleteChangeNotificationOf(update1Info));
        channel.asLifecycleObservable().subscribe(lifecycleSubscriber);
        lifecycleSubscriber.assertCompleted();
    }

    @Test
    public void testChannelDisconnectWithoutUnregister() throws Exception {
        // First registration
        incomingSubject.onNext(new Register(registerInfo));
        assertThat(dataSubscriber.takeNext(1, TimeUnit.SECONDS), EurekaMatchers.addChangeNotificationOf(registerInfo));

        // Simulated transport error
        incomingSubject.onError(new IOException("simulated transport error"));
        channel.asLifecycleObservable().subscribe(lifecycleSubscriber);
        lifecycleSubscriber.assertError(IOException.class);
    }

    @Test(timeout = 60000)
    public void testUnregisterOnIdleChannel() throws Exception {
        // First registration
        incomingSubject.onNext(new Unregister());
        assertThat(dataSubscriber.takeNext(200, TimeUnit.MILLISECONDS), is(nullValue()));

        channel.asLifecycleObservable().subscribe(lifecycleSubscriber);
        lifecycleSubscriber.assertError(IllegalArgumentException.class);
    }

    @Test(timeout = 60000)
    public void testReturnErrorOnceClosed() throws Exception {
        channel.close();

        incomingSubject.onNext(new Register(registerInfo));
        assertThat(dataSubscriber.takeNext(200, TimeUnit.MILLISECONDS), is(nullValue()));

        incomingSubject.onNext(Unregister.INSTANCE);
        assertThat(dataSubscriber.takeNext(200, TimeUnit.MILLISECONDS), is(nullValue()));
    }
}