package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.noop.NoOpReplicationChannelMetrics;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.replication.RegistryReplicator;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class RetryableSenderReplicationChannelTest {

    private static final long INITIAL_DELAY = 1000;

    private static final InstanceInfo INFO1 = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp").build();
    private static final InstanceInfo INFO2 = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp2").build();
    private static final InstanceInfo INFO3 = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp3").build();

    private final TestScheduler scheduler = Schedulers.test();

    private final MessageConnection mockMessageConnection = mock(MessageConnection.class);
    private final TransportClient mockClient = mock(TransportClient.class);

    private final ReplicationHello replicationHello = new ReplicationHello("111", 2);

    private SenderReplicationChannel delegateChannel1;
    private SenderReplicationChannel delegateChannel2;

    private RetryableSenderReplicationChannel channel;

    @Before
    public void setUp() throws Exception {
        when(mockClient.connect()).thenReturn(Observable.just(mockMessageConnection));

        delegateChannel1 = spy(new SenderReplicationChannel(mockClient, NoOpReplicationChannelMetrics.INSTANCE));
        delegateChannel2 = spy(new SenderReplicationChannel(mockClient, NoOpReplicationChannelMetrics.INSTANCE));

        Func0<ReplicationChannel> channelFactory = new Func0<ReplicationChannel>() {

            private final SenderReplicationChannel[] channels = {delegateChannel1, delegateChannel2};
            private int idx;

            @Override
            public SenderReplicationChannel call() {
                SenderReplicationChannel next = channels[idx];
                idx = (idx + 1) % channels.length;
                return next;
            }
        };

        // mock their methods so we don't actually execute them (and hence won't need to mock inside)
        mockDelegateChannelMethods(delegateChannel1);
        mockDelegateChannelMethods(delegateChannel2);

        TestScheduler registryScheduler = Schedulers.test();
        SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics(), registryScheduler);

        Source localSource = new Source(Source.Origin.LOCAL);
        registry.register(INFO1, localSource).subscribe();
        registry.register(INFO2, localSource).subscribe();
        registryScheduler.triggerActions();

        RegistryReplicator replicator = new RegistryReplicator("111", registry);

        channel = spy(new RetryableSenderReplicationChannel(channelFactory, replicator, INITIAL_DELAY, scheduler));
        verify(delegateChannel1, times(1)).hello(any(ReplicationHello.class));  // initial hello at create time
        verify(delegateChannel1, times(1)).register(INFO1);  // replication should have started and send INFO1
        verify(delegateChannel1, times(1)).register(INFO2);  // replication should have started and send INFO2
    }

    @After
    public void tearDown() throws Exception {
        channel.close();
    }

    @Test
    public void testForwardsRequestsToDelegate() throws Exception {
        channel.hello(replicationHello).subscribe();
        verify(delegateChannel1, times(2)).hello(any(ReplicationHello.class));  // 2 times, including the initial at create time

        channel.register(INFO3).subscribe();
        verify(delegateChannel1, times(1)).register(INFO3);

        InstanceInfo newInfo3 = new InstanceInfo.Builder().withInstanceInfo(INFO3).withVipAddress("aNewName").build();
        channel.register(newInfo3).subscribe();
        verify(delegateChannel1, times(1)).register(newInfo3);

        channel.unregister(INFO3.getId()).subscribe();
        verify(delegateChannel1, times(1)).unregister(INFO3.getId());
    }

    @Test
    public void testReconnectsWhenChannelFailure() throws Exception {
        // channel is already connected and replicating at setUp

        // Break the channel by closing it (sends OnComplete)
        delegateChannel1.close();

        // Verify that reconnected
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);
        verify(delegateChannel2, times(1)).register(INFO1);
        verify(delegateChannel2, times(1)).register(INFO2);
        verify(delegateChannel2, times(0)).register(INFO3);

        verify(delegateChannel1, times(1)).register(INFO1);  // make sure delegate1 is not called again
        verify(delegateChannel1, times(1)).register(INFO2);  // make sure delegate1 is not called again

        // Verify that new requests relayed to the new channel
        channel.register(INFO3).subscribe();
        verify(delegateChannel2, times(1)).register(INFO3);
        verify(delegateChannel1, times(0)).register(INFO3);
    }

    @Test
    public void testClosesInternalChannels() throws Exception {
        // channel is already connected and replicating at setUp

        // Break the channel by closing it (sends OnComplete)
        delegateChannel1.close();

        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);
        verify(delegateChannel1, atLeastOnce()).close();  // verify first channel is closed

        channel.close();
        verify(delegateChannel2, atLeastOnce()).close();  // verify second channel is closed
    }

    protected void mockDelegateChannelMethods(SenderReplicationChannel channel) {
        doReturn(Observable.just(new ReplicationHelloReply("222", false))).when(channel).hello(any(ReplicationHello.class));
        doReturn(Observable.<Void>empty()).when(channel).register(any(InstanceInfo.class));
        doReturn(Observable.<Void>empty()).when(channel).unregister(any(String.class));
    }
}