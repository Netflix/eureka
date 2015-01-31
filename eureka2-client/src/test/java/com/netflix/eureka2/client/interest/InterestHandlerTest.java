package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.TestChannelFactory;
import com.netflix.eureka2.channel.TestInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class InterestHandlerTest {

    private static final int RETRY_WAIT_MILLIS = 10;
    private final int failAfterMillis = 10;
    private final AtomicInteger channelId = new AtomicInteger(0);

    private MessageConnection messageConnection;

    private TransportClient transportClient;

    private Interest<InstanceInfo> discoveryInterest;
    private Interest<InstanceInfo> zuulInterest;
    private Interest<InstanceInfo> allInterest;

    private List<InstanceInfo> discoveryInfos;
    private List<InstanceInfo> zuulInfos;
    private List<InstanceInfo> allInfos;

    private TestScheduler registryScheduler;
    private SourcedEurekaRegistry<InstanceInfo> registry;

    private ChannelFactory<InterestChannel> mockFactory;
    private TestChannelFactory<InterestChannel> factory;
    private InterestHandler handler;

    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> discoverySubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> zuulSubscriber;
    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> allSubscriber;

    @Before
    public void setUp() {
        messageConnection = mock(MessageConnection.class);
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
        registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics(), registryScheduler);
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

        if (handler != null) {
            handler.shutdown();
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

        handler = new InterestHandlerImpl(registry, factory, RETRY_WAIT_MILLIS);

        List<ChangeNotification<InstanceInfo>> expectedDiscovery = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        handler.forInterest(discoveryInterest).subscribe(discoverySubscriber);
        discoverySubscriber.assertProducesInAnyOrder(expectedDiscovery, new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 30, TimeUnit.SECONDS);

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

        handler.forInterest(zuulInterest).subscribe(zuulSubscriber);
        zuulSubscriber.assertProducesInAnyOrder(expectedZuul, new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 30, TimeUnit.SECONDS);

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
        handler = new InterestHandlerImpl(registry, factory, RETRY_WAIT_MILLIS);

        List<ChangeNotification<InstanceInfo>> expected = Arrays.asList(
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(0)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(1)),
                SampleChangeNotification.DiscoveryAdd.newNotification(discoveryInfos.get(2))
        );

        handler.forInterest(discoveryInterest).subscribe(discoverySubscriber);
        discoverySubscriber.assertProducesInAnyOrder(expected, new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 30, TimeUnit.SECONDS);

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

        handler = new InterestHandlerImpl(registry, factory, RETRY_WAIT_MILLIS);

        handler.forInterest(discoveryInterest).subscribe(discoverySubscriber);

        assertThat(factory.getAllChannels().size(), is(1));
        TestInterestChannel channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.operations.size(), is(1));
        assertThat(channel0.closeCalled, is(false));
        assertThat(matchInterests(channel0.operations.iterator().next(), discoveryInterest), is(true));

        handler.forInterest(zuulInterest).subscribe(zuulSubscriber);

        assertThat(factory.getAllChannels().size(), is(1));
        channel0 = (TestInterestChannel) factory.getAllChannels().get(0);
        assertThat(channel0.id, is(0));
        assertThat(channel0.closeCalled, is(false));
        assertThat(channel0.operations.size(), is(2));
        Interest<InstanceInfo>[] operations = channel0.operations.toArray(new Interest[]{});
        assertThat(matchInterests(operations[0], discoveryInterest), is(true));
        assertThat(matchInterests(operations[1], new MultipleInterests<>(discoveryInterest, zuulInterest)), is(true));

        handler.shutdown();

        discoverySubscriber.assertOnCompleted();
        zuulSubscriber.assertOnCompleted();
    }

    private InterestChannel newAlwaysSuccessChannel(Integer id) {
        InterestChannel channel = spy(new InterestChannelImpl(registry, transportClient, mock(InterestChannelMetrics.class)));
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos);
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(zuulInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(zuulInfos);
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(allInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos);
                return Observable.empty();
            }
        });

        when(channel.change(argThat(new InterestMatcher(Interests.forFullRegistry())))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(allInfos);
                return Observable.empty();
            }
        });

        return new TestInterestChannel(channel, id);
    }

    private InterestChannel newAlwaysFailChannel(Integer id) {
        InterestChannel channel = spy(new InterestChannelImpl(registry, transportClient, mock(InterestChannelMetrics.class)));
        when(channel.change(any(Interest.class))).thenReturn(Observable.<Void>error(new Exception("test: operation error")));
        when(channel.change(any(MultipleInterests.class))).thenReturn(Observable.<Void>error(new Exception("test: operation error")));

        return new TestInterestChannel(channel, id);
    }

    private InterestChannel newSendingInterestFailChannel(Integer id) {
        final InterestChannel channel = spy(new InterestChannelImpl(registry, transportClient, mock(InterestChannelMetrics.class)));
        when(channel.change(argThat(new InterestMatcher(discoveryInterest)))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registerAll(discoveryInfos.subList(0, 1));
                return Observable.empty();
            }
        });

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

    private void registerAll(List<InstanceInfo> infos) {
        Source source = new Source(Source.Origin.LOCAL);
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
                return matchInterests(interest, (Interest)argument);
            } else if (argument instanceof MultipleInterests) {
                return matchInterests(interest, (MultipleInterests) argument);
            } else if (argument instanceof FullRegistryInterest) {
                return true;
            }
            return false;
        }
    }
}
