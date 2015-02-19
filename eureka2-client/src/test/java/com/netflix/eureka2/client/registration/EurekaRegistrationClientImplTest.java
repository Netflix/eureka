package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.TestChannelFactory;
import com.netflix.eureka2.channel.TestRegistrationChannel;
import com.netflix.eureka2.client.channel.RegistrationChannelFactory;
import com.netflix.eureka2.client.channel.RegistrationChannelImpl;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscriber;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class EurekaRegistrationClientImplTest {

    private static final int RETRY_WAIT_MILLIS = 10;
    private final AtomicInteger channelId = new AtomicInteger(0);

    private List<InstanceInfo> infos;

    private ReplaySubject<InstanceInfo> registrationSubject;
    private ChannelFactory<RegistrationChannel> mockFactory;
    private TestChannelFactory<RegistrationChannel> factory;
    private EurekaRegistrationClient client;

    private TestSubscriber<Void> initSubscriber;
    private TestSubscriber<Void> testSubscriber;

    @Before
    public void setUp() {
        InstanceInfo.Builder seed1 = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("id1")
                .withApp("app1");

        infos = Arrays.asList(
                seed1.withStatus(InstanceInfo.Status.STARTING).build(),
                seed1.withStatus(InstanceInfo.Status.UP).build(),
                seed1.withStatus(InstanceInfo.Status.DOWN).build()
        );

        registrationSubject = ReplaySubject.create();
        mockFactory = mock(RegistrationChannelFactory.class);
        factory = new TestChannelFactory<>(mockFactory);
        client = spy(new EurekaRegistrationClientImpl(factory, RETRY_WAIT_MILLIS));

        initSubscriber = new TestSubscriber<>();
        testSubscriber = new TestSubscriber<>();
    }

    @After
    public void cleanUp() {
        initSubscriber.unsubscribe();
        testSubscriber.unsubscribe();

        client.shutdown();
    }

    @Test
    public void testRegistrationLifecycle() {
        when(mockFactory.newChannel()).then(new Answer<RegistrationChannel>() {
            @Override
            public RegistrationChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        RegistrationObservable response = client.register(registrationSubject);
        response.initialRegistrationResult().subscribe(initSubscriber);
        response.subscribe(testSubscriber);

        // send the first registration information
        registrationSubject.onNext(infos.get(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        TestRegistrationChannel testChannel;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.hasUnregistered, is(false));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(1));
        assertThat(testChannel.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        // send the next info
        registrationSubject.onNext(infos.get(1));

        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.hasUnregistered, is(false));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(2));
        assertThat(testChannel.operations.toArray(), equalTo(infos.subList(0, 2).toArray()));

        // send the second info again. The distinctUntilChanged should make this a no-op
        registrationSubject.onNext(infos.get(1));

        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.hasUnregistered, is(false));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(2));
        assertThat(testChannel.operations.toArray(), equalTo(infos.subList(0, 2).toArray()));

        // send the third info
        registrationSubject.onNext(infos.get(2));

        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.hasUnregistered, is(false));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(3));
        assertThat(testChannel.operations.toArray(), equalTo(infos.toArray()));
    }

    @Test
    public void testRegistrationLifecycleWithRegistrationFailures() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<RegistrationChannel>() {
            @Override
            public RegistrationChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newAlwaysFailChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        RegistrationObservable response = client.register(registrationSubject);
        response.initialRegistrationResult().subscribe(initSubscriber);
        response.subscribe(testSubscriber);

        // send the first registration information
        registrationSubject.onNext(infos.get(0));

        TestRegistrationChannel testChannel0;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        factory.awaitChannels(2, RETRY_WAIT_MILLIS + 500);  // wait out the retry period configured for .retryWhen()

        // send the next info
        registrationSubject.onNext(infos.get(1));

        initSubscriber.assertTerminalEvent();   // now the init ob will onComplete
        initSubscriber.assertNoErrors();

        assertThat(factory.getAllChannels().size(), is(2));

        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        TestRegistrationChannel testChannel1;
        testChannel1 = (TestRegistrationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.hasUnregistered, is(false));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(2));
        assertThat(testChannel1.operations.toArray(), equalTo(infos.subList(0, 2).toArray()));

        // send the third info
        registrationSubject.onNext(infos.get(2));

        assertThat(factory.getAllChannels().size(), is(2));
        testChannel1 = (TestRegistrationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.hasUnregistered, is(false));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(3));
        assertThat(testChannel1.operations.toArray(), equalTo(infos.toArray()));
    }

    // note this is different to the above test as the error in this case come from the channel without any operation
    @Test
    public void testRetryOnChannelDisconnectError() throws Exception {
        final int failTimeMillis = 10;
        when(mockFactory.newChannel()).then(new Answer<RegistrationChannel>() {
            @Override
            public RegistrationChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newTimedFailChannel(channelId.getAndIncrement(), failTimeMillis);
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        RegistrationObservable response = client.register(registrationSubject);
        response.initialRegistrationResult().subscribe(initSubscriber);
        response.subscribe(testSubscriber);

        // send the first registration information
        registrationSubject.onNext(infos.get(0));

        initSubscriber.assertTerminalEvent();   // now the init ob will onComplete
        initSubscriber.assertNoErrors();

        TestRegistrationChannel testChannel0;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(false));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        factory.awaitChannels(2, failTimeMillis + RETRY_WAIT_MILLIS + 10);  // wait out the retry period configured for .retryWhen()

        assertThat(factory.getAllChannels().size(), is(2));

        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat  (testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        TestRegistrationChannel testChannel1;
        testChannel1 = (TestRegistrationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.hasUnregistered, is(false));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(1));
        assertThat(testChannel1.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        // now send the next info
        registrationSubject.onNext(infos.get(1));

        assertThat(factory.getAllChannels().size(), is(2));

        testChannel1 = (TestRegistrationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.hasUnregistered, is(false));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(2));
        assertThat(testChannel1.operations.toArray(), equalTo(infos.subList(0, 2).toArray()));
    }

    @Test
    public void testUnregisterOnUnsubscribe() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<RegistrationChannel>() {
            @Override
            public RegistrationChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        RegistrationObservable response = client.register(registrationSubject);
        response.initialRegistrationResult().subscribe(initSubscriber);
        response.subscribe(testSubscriber);

        // send the first registration information
        registrationSubject.onNext(infos.get(0));

        initSubscriber.assertTerminalEvent();   // now the init ob will onComplete
        initSubscriber.assertNoErrors();

        TestRegistrationChannel testChannel0;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(false));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        // now unregister
        testSubscriber.unsubscribe();

        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(true));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));
    }

    @Test
    public void testMultipleSubscriberOnSameRegistrationLifecycle() {
        TestSubscriber<Void> anotherSubscriber = new TestSubscriber<>();

        when(mockFactory.newChannel()).then(new Answer<RegistrationChannel>() {
            @Override
            public RegistrationChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        RegistrationObservable response = client.register(registrationSubject);
        response.subscribe(testSubscriber);
        response.subscribe(anotherSubscriber);

        // send the first registration information
        registrationSubject.onNext(infos.get(0));

        TestRegistrationChannel testChannel0;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel0 = (TestRegistrationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(false));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        // now unregister with one subscriber
        testSubscriber.unsubscribe();

        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(false));
        assertThat(testChannel0.closeCalled, is(false));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));

        // unregister should only succeed when all subscribers have unregistered
        anotherSubscriber.unsubscribe();

        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.hasUnregistered, is(true));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.toArray(), equalTo(infos.subList(0, 1).toArray()));
    }


    private static RegistrationChannel newAlwaysSuccessChannel(Integer id) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        RegistrationChannel channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));

        return new TestRegistrationChannel(channel, id);
    }

    private static RegistrationChannel newAlwaysFailChannel(Integer id) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>error(new Exception("test: registration error")));

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        RegistrationChannel channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));

        return new TestRegistrationChannel(channel, id);
    }

    private static RegistrationChannel newTimedFailChannel(Integer id, int failAfterMillis) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        final RegistrationChannel channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));

        Observable.empty().delay(failAfterMillis, TimeUnit.MILLISECONDS).subscribe(new Subscriber<Object>() {
            @Override
            public void onCompleted() {
                channel.close(new Exception("test: channel error"));
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Object o) {
            }
        });

        return new TestRegistrationChannel(channel, id);
    }

    private static MessageConnection newMockMessageConnection() {
        final ReplaySubject<Void> lifecycleSubject = ReplaySubject.create();
        MessageConnection messageConnection = mock(MessageConnection.class);
        when(messageConnection.lifecycleObservable()).thenReturn(lifecycleSubject);
        return messageConnection;
    }
}
