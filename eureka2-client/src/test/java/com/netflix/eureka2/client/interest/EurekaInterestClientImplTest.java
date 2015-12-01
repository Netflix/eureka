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
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.interest.StdFullRegistryInterest;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class EurekaInterestClientImplTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private static final int RETRY_WAIT_MILLIS = 10;
    public static final int FAILING_CHANNEL_FAILURE_DELAY_MS = 10;

    private final AtomicInteger channelId = new AtomicInteger(0);

    private TransportClient transportClient;

    private Interest<InstanceInfo> discoveryInterest;
    private Interest<InstanceInfo> zuulInterest;
    private Interest<InstanceInfo> allInterest;

    private List<InstanceInfo> discoveryInfos;
    private List<InstanceInfo> zuulInfos;
    private List<InstanceInfo> allInfos;

    private TestScheduler testScheduler;
    private EurekaRegistry<InstanceInfo> registry;

    private ChannelFactory<InterestChannel> mockFactory;
    private TestChannelFactory<InterestChannel> factory;
    private EurekaInterestClient client;

    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> discoverySubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> zuulSubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> allSubscriber;

    private TestInterestChannel channel0;
    private TestInterestChannel channel1;

    @Before
    public void setUp() {
        EurekaConnection messageConnection = mock(EurekaConnection.class);
        final ReplaySubject<Void> lifecycleSubject = ReplaySubject.create();
        when(messageConnection.lifecycleObservable()).thenReturn(lifecycleSubject);
        when(messageConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.incoming()).thenReturn(Observable.never());

        transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        discoveryInterest = SampleInterest.DiscoveryApp.build();
        zuulInterest = SampleInterest.ZuulApp.build();
        allInterest = InterestModel.getDefaultModel().newMultipleInterests(discoveryInterest, zuulInterest);

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

        testScheduler = Schedulers.test();

        EurekaRegistry<InstanceInfo> delegateRegistry = new EurekaRegistryImpl(
                new IndexRegistryImpl<InstanceInfo>(), EurekaRegistryMetricFactory.registryMetrics(), testScheduler);
        registry = delegateRegistry;

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

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

        List<ChangeNotification<InstanceInfo>> expectedDiscovery = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(discoverySubscriber);
        discoverySubscriber.assertContainsInAnyOrder(expectedDiscovery);

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
        zuulSubscriber.assertContainsInAnyOrder(expectedZuul);

        assertThat(factory.getAllChannels().size(), is(1));
        channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.closeCalled, is(false));
        assertThat(channel0.operations.size(), is(2));
        Interest<InstanceInfo>[] operations = channel0.operations.toArray(new Interest[]{});
        assertThat(matchInterests(operations[0], discoveryInterest), is(true));
        assertThat(matchInterests(operations[1], InterestModel.getDefaultModel().newMultipleInterests(discoveryInterest, zuulInterest)), is(true));
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

        doChannelFailOverTest();
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

        doChannelFailOverTest();
    }

    private void doChannelFailOverTest() throws Exception {
        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

        List<ChangeNotification<InstanceInfo>> expected = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(discoverySubscriber);

        testScheduler.advanceTimeBy(RETRY_WAIT_MILLIS + FAILING_CHANNEL_FAILURE_DELAY_MS, TimeUnit.MILLISECONDS);

        discoverySubscriber.assertContainsInAnyOrder(expected);
        assertThat(factory.getAllChannels().size(), is(2));

        channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(true));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));

        channel1 = (TestInterestChannel) factory.getAllChannels().get(1);
        assertThat(channel1.id, is(1));
        assertThat(channel1.operations.size(), is(1));
        assertThat(channel1.closeCalled, is(false));
        assertThat(matchInterests(channel1.operations.iterator().next(), discoveryInterest), is(true));

        // verify that a new subscriber can subscribe and see latest changes
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> newSubscriber = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(newSubscriber);

        newSubscriber.assertContainsInAnyOrder(expected);

        InstanceInfo update = new StdInstanceInfo.Builder().withInstanceInfo(discoveryInfos.get(0))
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        Observable<ChangeNotification<InstanceInfo>> updateOb = Observable.just(
                new ChangeNotification<>(ChangeNotification.Kind.Add, update)
        );

        registry.connect(channel1.getSource(), updateOb).subscribe();
        testScheduler.triggerActions();

        assertThat(newSubscriber.takeNext(), is(modifyChangeNotificationOf(update)));
    }

    @Test
    public void testShutdownCleanUpResources() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

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
        assertThat(matchInterests(operations[1], InterestModel.getDefaultModel().newMultipleInterests(discoveryInterest, zuulInterest)), is(true));

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

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

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

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

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

        client = new EurekaInterestClientImpl(registry, factory, RETRY_WAIT_MILLIS, testScheduler);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber1 = new ExtTestSubscriber<>();
        client.forInterest(discoveryInterest).filter(dataOnlyFilter()).subscribe(extTestSubscriber1);

        // don't use all registry interest as it is a special singleton
        Interest<InstanceInfo> compositeInterest = InterestModel.getDefaultModel().newMultipleInterests(discoveryInterest, zuulInterest);

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
        doChannelFailOverTest();

        // Verify data copies from the first channel are evicted
        List<? extends MultiSourcedDataHolder<InstanceInfo>> holders =
                registry.getHolders().take(3).toList().timeout(1, TimeUnit.SECONDS).toBlocking().first();

        for (MultiSourcedDataHolder<InstanceInfo> holder : holders) {
            assertThat("Registry data holder expected to contain only one copy", holder.getAllSources().size(), is(equalTo(1)));
            assertThat(
                    "Registry data holder expected to contain copy from the second channel",
                    holder.get(channel1.getSource()), is(notNullValue())
            );
        }
    }


    // -----------------------------------------------------------------------------

    private InterestChannel createInterestChannel() {
        return spy(new ClientInterestChannel(registry, transportClient, 0, mock(InterestChannelMetrics.class)));
    }

    private TestInterestChannel newAlwaysSuccessChannel(Integer id) {
        final InterestChannel channel = createInterestChannel();
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos, channel.getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(zuulInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(zuulInfos, channel.getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(allInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos, channel.getSource());
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(Interests.forFullRegistry())))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos, channel.getSource());
                return Observable.empty();
            }
        });

        return new TestInterestChannel(channel, id);
    }

    private TestInterestChannel newAlwaysFailChannel(Integer id) {
        final Exception e = new Exception("test: operation error");

        final InterestChannel channel = createInterestChannel();
        when(channel.change(any(Interest.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                channel.close(e);  // link up with the channel lifecycle as we can't mock that
                return Observable.error(e);
            }
        });
        when(channel.change(any(MultipleInterests.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                channel.close(e);  // link up with the channel lifecycle as we can't mock that
                return Observable.error(e);
            }
        });

        return new TestInterestChannel(channel, id);
    }

    private TestInterestChannel newSendingInterestFailChannel(Integer id) {
        final InterestChannel channel = createInterestChannel();
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos.subList(0, 2), channel.getSource());
                return Observable.empty();
            }
        });

        Observable.empty().delay(FAILING_CHANNEL_FAILURE_DELAY_MS, TimeUnit.MILLISECONDS, testScheduler)
                .subscribe(new Subscriber<Object>() {
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
        ReplaySubject<ChangeNotification<InstanceInfo>> dataStream = ReplaySubject.create();
        registry.connect(source, dataStream).subscribe();
        for (InstanceInfo info : infos) {
            dataStream.onNext(new ChangeNotification<>(ChangeNotification.Kind.Add, info));
            testScheduler.triggerActions();
        }
    }

    // easiest way is to cast both interests to multipleInterests and apply equals
    private static boolean matchInterests(Interest<InstanceInfo> one, Interest<InstanceInfo> two) {
        return InterestModel.getDefaultModel().newMultipleInterests(one).equals(InterestModel.getDefaultModel().newMultipleInterests(two));
    }

    static class InterestMatcher extends ArgumentMatcher<Interest<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;

        InterestMatcher(Interest<InstanceInfo> interest) {
            this.interest = interest;
        }

        @Override
        public boolean matches(Object argument) {
            if (argument instanceof Interest) {
                return matchInterests(interest, (Interest) argument);
            } else if (argument instanceof MultipleInterests) {
                return matchInterests(interest, (MultipleInterests) argument);
            } else if (argument instanceof StdFullRegistryInterest) {
                return true;
            }
            return false;
        }
    }
}

