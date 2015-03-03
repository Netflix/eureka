package com.netflix.eureka2.server.interest;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class FullFetchInterestClientTest {

    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_1 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());
    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_2 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());
    private static final ChangeNotification<InstanceInfo> ADD_ANOTHER_VIP = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaReadServer.build());

    private static final Interest<InstanceInfo> INTEREST = Interests.forVips(ADD_INSTANCE_1.getData().getVipAddress());

    private static final ChangeNotification<InstanceInfo> BUFFER_BEGIN = StreamStateNotification.bufferStartNotification(Interests.forFullRegistry());
    private static final ChangeNotification<InstanceInfo> BUFFER_END = StreamStateNotification.bufferEndNotification(Interests.forFullRegistry());

    private static final Source SOURCE = new Source(Origin.INTERESTED, "test");

    private final TestScheduler testScheduler = Schedulers.test();

    private final BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new FullFetchBatchingRegistry<>();
    private final IndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(
            new IndexRegistryImpl<InstanceInfo>(),
            remoteBatchingRegistry
    );
    private final SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(indexRegistry, registryMetrics(), testScheduler);

    private final ChannelFactory<InterestChannel> channelFactory = mock(ChannelFactory.class);

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
    private final InterestChannel interestChannel = mock(InterestChannel.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationsSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(channelFactory.newChannel()).thenReturn(interestChannel);
        remoteBatchingRegistry.connectTo(notificationsSubject);
    }

    @Test
    public void testChannelHasSingleFullRegistryFetchSubscription() throws Exception {
        new FullFetchInterestClient(registry, channelFactory).forInterest(INTEREST).subscribe(testSubscriber);
        testScheduler.triggerActions();

        verify(interestChannel, times(1)).change(Interests.forFullRegistry());
        verify(interestChannel, times(0)).change(INTEREST);
    }

    @Test
    public void testBufferMarkersFromTheChannelArePropagatedToSubscriber() throws Exception {
        new FullFetchInterestClient(registry, channelFactory).forInterest(INTEREST).subscribe(testSubscriber);

        notificationsSubject.onNext(BUFFER_BEGIN);
        testScheduler.triggerActions();

        registry.register(ADD_INSTANCE_1.getData(), SOURCE).subscribe();
        registry.register(ADD_INSTANCE_2.getData(), SOURCE).subscribe();
        testScheduler.triggerActions();

        notificationsSubject.onNext(BUFFER_END);
        testScheduler.triggerActions();

        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_1)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_2)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ChangeNotification.<InstanceInfo>bufferSentinel())));
    }

    @Test
    public void testBufferMarkersFromRegistryArePropagatedToSubscribers() throws Exception {
        notificationsSubject.onNext(StreamStateNotification.bufferEndNotification(Interests.forFullRegistry()));

        registry.register(ADD_INSTANCE_1.getData(), SOURCE).subscribe();
        registry.register(ADD_INSTANCE_2.getData(), SOURCE).subscribe();
        registry.register(ADD_ANOTHER_VIP.getData(), SOURCE).subscribe();
        testScheduler.triggerActions();

        new FullFetchInterestClient(registry, channelFactory).forInterest(INTEREST).subscribe(testSubscriber);

        assertThat(testSubscriber.takeNext(2), containsInAnyOrder(ADD_INSTANCE_1, ADD_INSTANCE_2));
        assertThat(testSubscriber.takeNext(), is(equalTo(ChangeNotification.<InstanceInfo>bufferSentinel())));
    }
}