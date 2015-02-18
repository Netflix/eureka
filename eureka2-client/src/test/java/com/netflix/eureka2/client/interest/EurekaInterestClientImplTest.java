package com.netflix.eureka2.client.interest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.TestChannelFactory;
import com.netflix.eureka2.channel.TestInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.PercentageDropEvictionStrategy;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.junit.EurekaMatchers;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class EurekaInterestClientImplTest {

    private static final int RETRY_WAIT_MILLIS = 10;
    private static final long EVICTION_TIMEOUT_MS = 30 * 1000;
    private static final int EVICTION_ALLOWED_PERCENTAGE_DROP_THRESHOLD = 50;//%
    private final AtomicInteger channelId = new AtomicInteger(0);

    private TransportClient transportClient;

    private Interest<InstanceInfo> discoveryInterest;
    private Interest<InstanceInfo> zuulInterest;
    private Interest<InstanceInfo> allInterest;

    private List<InstanceInfo> discoveryInfos;
    private List<InstanceInfo> zuulInfos;
    private List<InstanceInfo> allInfos;

    private TestScheduler registryScheduler;
    private TestScheduler evictionScheduler;
    private PreservableEurekaRegistry registry;
    private EvictionQueue evictionQueue;

    private ChannelFactory<InterestChannel> mockFactory;
    private TestChannelFactory<InterestChannel> factory;
    private EurekaInterestClient client;

    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> discoverySubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> zuulSubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> allSubscriber;

    @Before
    public void setUp() {
        MessageConnection messageConnection = mock(MessageConnection.class);
        final ReplaySubject<Void> lifecycleSubject = ReplaySubject.create();
        when(messageConnection.lifecycleObservable()).thenReturn(lifecycleSubject);
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.incoming()).thenReturn(Observable.never());

        transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        discoveryInterest = SampleInterest.DiscoveryApp.build();
        zuulInterest = SampleInterest.ZuulApp.build();
        allInterest = new MultipleInterests<>(discoveryInterest, zuulInterest);

        discoveryInfos = Arrays.asList(
                SampleInstanceInfo.DiscoveryServer.build(),
                SampleInstanceInfo.DiscoveryServer.build(),
                SampleInstanceInfo.DiscoveryServer.build()
        );

        zuulInfos = Arrays.asList(
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.ZuulServer.build()
        );

        allInfos = new ArrayList<>(discoveryInfos);
        allInfos.addAll(zuulInfos);

        registryScheduler = Schedulers.test();
        evictionScheduler = Schedulers.test();
        evictionQueue = spy(new EvictionQueueImpl(EVICTION_TIMEOUT_MS, EurekaRegistryMetricFactory.registryMetrics(), evictionScheduler));

        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
        IndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(new IndexRegistryImpl<InstanceInfo>(), remoteBatchingRegistry);
        SourcedEurekaRegistry<InstanceInfo> delegateRegistry = new SourcedEurekaRegistryImpl(
                indexRegistry, EurekaRegistryMetricFactory.registryMetrics(),
                registryScheduler
        );
        registry = spy(new PreservableEurekaRegistry(
                delegateRegistry,
                evictionQueue,
                new PercentageDropEvictionStrategy(EVICTION_ALLOWED_PERCENTAGE_DROP_THRESHOLD),
                EurekaRegistryMetricFactory.registryMetrics()));

        mockFactory = mock(InterestChannelFactory.class);
        factory = new TestChannelFactory<>(mockFactory);

        discoverySubscriber = new ExtTestSubscriber<>();
        zuulSubscriber = new ExtTestSubscriber<>();
        allSubscriber = new ExtTestSubscriber<>();
    }

    @After
    public void tearDown() {
        discoverySubscriber.unsubscribe();
        zuulSubscriber.unsubscribe();
        allSubscriber.unsubscribe();

        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void testForInterestHappyCase() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        List<ChangeNotification<InstanceInfo>> expectedDiscovery = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(discoverySubscriber);
        discoverySubscriber.assertProducesInAnyOrder(expectedDiscovery);

        assertThat(factory.getAllChannels().size(), is(1));
        TestInterestChannel channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(false));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));


        List<ChangeNotification<InstanceInfo>> expectedZuul = Arrays.asList(
                SampleChangeNotification.ZuulAdd.newNotification(zuulInfos.get(0)),
                SampleChangeNotification.ZuulAdd.newNotification(zuulInfos.get(1)),
                SampleChangeNotification.ZuulAdd.newNotification(zuulInfos.get(2))
        );

        client.forInterest(zuulInterest).filter(dataOnlyFilter()).subscribe(zuulSubscriber);
        zuulSubscriber.assertProducesInAnyOrder(expectedZuul);

        assertThat(factory.getAllChannels().size(), is(1));
        channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.closeCalled, is(false));
        assertThat(channel0.operations.size(), is(2));
        Interest<InstanceInfo>[] operations = channel0.operations.toArray(new Interest[]{});
        assertThat(matchInterests(operations[0], discoveryInterest), is(true));
        assertThat(matchInterests(operations[1], new MultipleInterests<>(discoveryInterest, zuulInterest)), is(true));
    }

    @Test
    public void testNewForInterestOperationFailure() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newAlwaysFailChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        testChannelFailOver();
    }

    // note this is different to the above test as the error in this case come from the channel without any operation
    @Test
    public void testRetryOnChannelDisconnectError() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newSendingInterestFailChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        testChannelFailOver();
    }

    private void testChannelFailOver() throws Exception {
        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        List<ChangeNotification<InstanceInfo>> expected = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(discoverySubscriber);
        discoverySubscriber.assertProducesInAnyOrder(expected);

        assertThat(factory.getAllChannels().size(), is(2));

        TestInterestChannel channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(true));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));

        TestInterestChannel channel1 = (TestInterestChannel) factory.getAllChannels().get(1);
        assertThat(channel1.id, is(1));
        assertThat(channel1.operations.size(), is(1));
        assertThat(channel1.closeCalled, is(false));
        assertThat(matchInterests(channel1.operations.iterator().next(), discoveryInterest), is(true));
    }

    @Test
    public void testShutdownCleanUpResources() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        client.forInterest(discoveryInterest).subscribe(discoverySubscriber);

        assertThat(factory.getAllChannels().size(), is(1));
        TestInterestChannel channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(false));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));

        client.forInterest(zuulInterest).subscribe(zuulSubscriber);

        assertThat(factory.getAllChannels().size(), is(1));
        channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.closeCalled, is(false));
        assertThat(channel0.operations.size(), is(2));
        Interest<InstanceInfo>[] operations = channel0.operations.toArray(new Interest[]{});
        assertThat(matchInterests(operations[0], discoveryInterest), is(true));
        assertThat(matchInterests(operations[1], new MultipleInterests<>(discoveryInterest, zuulInterest)), is(true));

        client.shutdown();

        discoverySubscriber.assertOnCompleted();
        zuulSubscriber.assertOnCompleted();
    }

    @Test(timeout = 60000)
    public void testForInterestSameTwoUsers() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber1 = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber1);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber2 = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber2);

        final List<InstanceInfo> discoveryOutput1 = new ArrayList<>();
        final List<InstanceInfo> discoveryOutput2 = new ArrayList<>();

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber1.takeNext(3, 500, TimeUnit.MILLISECONDS)) {
            discoveryOutput1.add(notification.getData());
        }

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber2.takeNext(3, 500, TimeUnit.MILLISECONDS)) {
            discoveryOutput2.add(notification.getData());
        }

        assertThat(discoveryOutput1, containsInAnyOrder(discoveryInfos.toArray()));
        assertThat(discoveryOutput2, containsInAnyOrder(discoveryInfos.toArray()));
    }

    @Test(timeout = 60000)
    public void testForInterestDifferentTwoUsers() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber1 = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber1);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber2 = new ExtTestSubscriber<>();
        client.forInterest(zuulInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber2);

        final List<InstanceInfo> discoveryOutput = new ArrayList<>();
        final List<InstanceInfo> zuulOutput = new ArrayList<>();

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber1.takeNext(3, 500, TimeUnit.MILLISECONDS)) {
            discoveryOutput.add(notification.getData());
        }

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber2.takeNext(3, 500, TimeUnit.MILLISECONDS)) {
            zuulOutput.add(notification.getData());
        }

        assertThat(discoveryOutput, containsInAnyOrder(discoveryInfos.toArray()));
        assertThat(zuulOutput, containsInAnyOrder(zuulInfos.toArray()));
    }

    @Test(timeout = 60000)
    public void testForInterestSecondInterestSupercedeFirst() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber1 = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber1);

        // don't use all registry interest as it is a special singleton
        Interest<InstanceInfo> compositeInterest = new MultipleInterests<>(discoveryInterest, zuulInterest);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber2 = new ExtTestSubscriber<>();
        client.forInterest(compositeInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber2);

        final List<InstanceInfo> discoveryOutput = new ArrayList<>();
        final List<InstanceInfo> compositeOutput = new ArrayList<>();

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber1.takeNext(3, 500, TimeUnit.MILLISECONDS)) {
            discoveryOutput.add(notification.getData());
        }

        for (ChangeNotification<InstanceInfo> notification : extTestSubscriber2.takeNext(6, 500, TimeUnit.MILLISECONDS)) {
            compositeOutput.add(notification.getData());
        }

        assertThat(discoveryOutput, containsInAnyOrder(discoveryInfos.toArray()));

        List<InstanceInfo> compositeExpected = new ArrayList<>(discoveryInfos);
        compositeExpected.addAll(zuulInfos);
        assertThat(compositeOutput, containsInAnyOrder(compositeExpected.toArray()));
    }

    @Test
    public void testNewChannelCreationEvictAllOlderSourcedRegistryData() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newSendingInterestFailChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS);

        List<ChangeNotification<InstanceInfo>> expected = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        ArgumentCaptor<InstanceInfo> infoCaptor = ArgumentCaptor.forClass(InstanceInfo.class);
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);

        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(discoverySubscriber);

        discoverySubscriber.assertProducesInAnyOrder(expected, new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 10, TimeUnit.SECONDS);

        assertThat(factory.getAllChannels().size(), is(2));

        TestInterestChannel channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(true));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));

        TestInterestChannel channel1 = (TestInterestChannel) factory.getAllChannels().get(1);
        assertThat(channel1.id, is(1));
        assertThat(channel1.operations.size(), is(1));
        assertThat(channel1.closeCalled, is(false));
        assertThat(matchInterests(channel1.operations.iterator().next(), discoveryInterest), is(true));

        assertThat(evictionQueue.size(), is(2));  // 2 stuck in queue (because we have not yet advanced the evictionQueue scheduler

        verify(evictionQueue, times(2)).add(infoCaptor.capture(), sourceCaptor.capture());

        assertThat(infoCaptor.getAllValues(), containsInAnyOrder(discoveryInfos.subList(0, 2).toArray()));
        Source channel0Source = channel0.getSource();
        assertThat(sourceCaptor.getAllValues().get(0), is(channel0Source));
        assertThat(sourceCaptor.getAllValues().get(1), is(channel0Source));
        assertThat(sourceCaptor.getAllValues().size(), is(2));

        evictionScheduler.advanceTimeBy(EVICTION_TIMEOUT_MS + 1, TimeUnit.MILLISECONDS);
        registryScheduler.advanceTimeBy(EVICTION_TIMEOUT_MS + 1, TimeUnit.MILLISECONDS);  // need to advance the registry scheduler to execute the unregisters from eviction

        evictionScheduler.advanceTimeBy(EVICTION_TIMEOUT_MS + 1, TimeUnit.MILLISECONDS);
        registryScheduler.advanceTimeBy(EVICTION_TIMEOUT_MS + 1, TimeUnit.MILLISECONDS);

        assertThat(evictionQueue.size(), is(0));  // all gone

        // verify that a new subscriber can subscribe and see latest changes
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> newSubscriber = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(newSubscriber);

        newSubscriber.assertProducesInAnyOrder(expected, new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 10, TimeUnit.SECONDS);

        InstanceInfo update = new InstanceInfo.Builder().withInstanceInfo(discoveryInfos.get(0))
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        registry.register(update, channel1.getSource()).subscribe();
        registryScheduler.triggerActions();

        assertThat(newSubscriber.takeNext(1, 5, TimeUnit.SECONDS).get(0), EurekaMatchers.modifyChangeNotificationOf(update));
    }


    // -----------------------------------------------------------------------------

    private InterestChannel createInterestChannel() {
        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
        return spy(new InterestChannelImpl(registry, remoteBatchingRegistry, transportClient, mock(InterestChannelMetrics.class)));
    }

    private TestInterestChannel newAlwaysSuccessChannel(Integer id) {
        final InterestChannel channel = createInterestChannel();
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos, ((Sourced) channel).getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(zuulInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(zuulInfos, ((Sourced) channel).getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(allInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos, ((Sourced) channel).getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(Interests.forFullRegistry())))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos, ((Sourced) channel).getSource());
                return Observable.empty();
            }
        });

        return new TestInterestChannel(channel, id);
    }

    private TestInterestChannel newAlwaysFailChannel(Integer id) {
        final InterestChannel channel = createInterestChannel();
        when(channel.change(any(Interest.class))).thenReturn(Observable.<Void>error(new Exception("test: operation error")));
        when(channel.change(any(MultipleInterests.class))).thenReturn(Observable.<Void>error(new Exception("test: operation error")));

        return new TestInterestChannel(channel, id);
    }

    private TestInterestChannel newSendingInterestFailChannel(Integer id) {
        final InterestChannel channel = createInterestChannel();
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos.subList(0, 2), ((Sourced) channel).getSource());
                return Observable.empty();
            }
        });

        int failAfterMillis = 10;
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

        return new TestInterestChannel(channel, id);
    }

    private void registerAll(List<InstanceInfo> infos, Source source) {
        for (InstanceInfo info : infos) {
            registry.register(info, source).subscribe();
            registryScheduler.triggerActions();
        }
    }

    // easiest way is to cast both interests to multipleInterests and apply equals
    private static <T> boolean matchInterests(Interest<T> one, Interest<T> two) {
        return new MultipleInterests<>(one).equals(new MultipleInterests<>(two));
    }

    static class InterestMatcher extends ArgumentMatcher<Interest<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;

        public InterestMatcher(Interest<InstanceInfo> interest) {
            this.interest = interest;
        }

        @Override
        public boolean matches(Object argument) {
            if (argument instanceof Interest) {
                return matchInterests(interest, (Interest) argument);
            } else if (argument instanceof MultipleInterests) {
                return matchInterests(interest, (MultipleInterests) argument);
            } else if (argument instanceof FullRegistryInterest) {
                return true;
            }
            return false;
        }
    }

}

