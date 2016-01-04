package com.netflix.eureka2.integration.batching;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.mockito.Mockito.spy;

/**
 * @author David Liu
 */
@Category({IntegrationTest.class, LongRunningTest.class})
@Ignore
public class ReplicationBatchHintsIntegrationTest extends AbstractBatchHintsIntegrationTest {

//    private ChannelSet channelSet1;
//    private ChannelSet channelSet2;
//    private ChannelSet channelSet3;
//    private ChannelSet channelSet4;

    private EurekaRegistry<InstanceInfo> registry;

    @Before
    public void setUp() {
        registry = spy(new EurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics()));

//        channelSet1 = new ChannelSet();
//        channelSet2 = new ChannelSet();
//        channelSet3 = new ChannelSet();
//        channelSet4 = new ChannelSet();
//
//        SelfInfoResolver selfInfoResolver = mock(SelfInfoResolver.class);
//        when(selfInfoResolver.resolve()).thenReturn(Observable.just(SampleInstanceInfo.CliServer.build()));
    }

//    static class ChannelSet {
//        final ReplaySubject<Object> incomingSubject;
//        final ReplaySubject<Void> serverConnectionLifecycle;
//        final EurekaConnection connection;
//
//        private ChannelSet() {
//            incomingSubject = ReplaySubject.create();
//            serverConnectionLifecycle = ReplaySubject.create();
//
//            connection = mock(EurekaConnection.class);
//            when(connection.incoming()).thenReturn(incomingSubject);
//            when(connection.submit(any(ReplicationHelloReply.class))).thenReturn(Observable.<Void>empty());
//            when(connection.acknowledge()).thenReturn(Observable.<Void>empty());
//            when(connection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);
//        }
//    }

    @After
    public void tearDown() {
    }

    /**
     * In this test, channel1 and channel2 are connected to different replication peers.
     * Channel3 and channel4 connects to the same peer as channel2.
     * Verification passes when we assert that channel2 and channel3 data are evicted when
     * channel4 receives a bufferEnd.
     */
    @Test
    public void testReceiverReplicationChannelChangeEvictionWithOtherChannelsActive() throws Exception {
//        final Interest<InstanceInfo> interest = Interests.forFullRegistry();
//
//        final List<InterestSetNotification> data1 = Arrays.asList(
//                newBufferStart(interest),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                SampleAddInstance.ZuulAdd.newMessage(),
//                newBufferEnd(interest)
//        );
//
//        int data1Size = data1.size() - 2;  // less buffer markers
//
//        final Observable<InterestSetNotification> remoteData1 = Observable.from(data1);
//
//        final List<InterestSetNotification> data2 = Arrays.asList(
//                newBufferStart(interest),
//                SampleAddInstance.DiscoveryAdd.newMessage(),
//                SampleAddInstance.DiscoveryAdd.newMessage(),
//                SampleAddInstance.DiscoveryAdd.newMessage(),
//                SampleAddInstance.DiscoveryAdd.newMessage(),
//                SampleAddInstance.DiscoveryAdd.newMessage(),
//                newBufferEnd(interest)
//        );
//
//        int data2Size = data2.size() - 2;  // less buffer markers
//
//        final Observable<InterestSetNotification> remoteData2 = Observable.from(data2);
//
//        ReceiverReplicationChannel channel1 = handler.doHandle(channelSet1.connection);
//        channel1.asLifecycleObservable().subscribe();
//
//        ReceiverReplicationChannel channel2 = handler.doHandle(channelSet2.connection);
//        channel2.asLifecycleObservable().subscribe();
//
//        Source senderSource1 = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "removeServer1", 0);
//        channelSet1.incomingSubject.onNext(ProtocolModel.getDefaultModel().newReplicationHello(senderSource1, data1Size));  // subtract the buffer markers
//        remoteData1.concatWith(Observable.<InterestSetNotification>never()).subscribe(channelSet1.incomingSubject);
//
//        Source senderSource2 = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "removeServer2", 0);
//        channelSet2.incomingSubject.onNext(ProtocolModel.getDefaultModel().newReplicationHello(senderSource2, data2Size));
//        remoteData2.concatWith(Observable.<InterestSetNotification>never()).subscribe(channelSet2.incomingSubject);
//
//        Thread.sleep(500);  // let the registry run as it's on a different loop
//
//        verifyRegistryContentSourceEntries(registry, channel1.getSource(), data1Size);
//        verifyRegistryContentSourceEntries(registry, channel2.getSource(), data2Size);
//
//        channel2.close();
//        ReceiverReplicationChannel channel3 = handler.doHandle(channelSet3.connection);
//        channel3.asLifecycleObservable().subscribe();
//
//        Source senderSource3 = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "removeServer2", 1);
//        channelSet3.incomingSubject.onNext(ProtocolModel.getDefaultModel().newReplicationHello(senderSource3, data2Size));
//        // test an unclean channel, where we received 1 less from data2 and also did not see the bufferEnd
//        // this should mean the last entry from data2 is still marked as from source2 and there are no eviction.
//        remoteData2.take(data2.size() - 2).concatWith(Observable.<InterestSetNotification>never()).subscribe(channelSet3.incomingSubject);
//
//        Thread.sleep(500);  // let the registry run as it's on a different loop
//
//        verifyRegistryContentSourceEntries(registry, channel1.getSource(), data1Size);
//        verifyRegistryContentSourceEntries(registry, channel2.getSource(), 1);
//        verifyRegistryContentSourceEntries(registry, channel3.getSource(), data2Size - 1);  // 1 less than expected
//
//        channel3.close();
//        ReceiverReplicationChannel channel4 = handler.doHandle(channelSet4.connection);
//        channel4.asLifecycleObservable().subscribe();
//
//        Source senderSource4 = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "removeServer2", 2);
//        channelSet4.incomingSubject.onNext(ProtocolModel.getDefaultModel().newReplicationHello(senderSource4, data2Size));
//        // test data difference in the channel, where we received 2 less from data2 BUT we also saw a bufferEnd
//        // this should mean content from data2 is 2 less from the original list and all other source types are evicted
//        remoteData2.take(data2.size() - 3).concatWith(Observable.<InterestSetNotification>never()).subscribe(channelSet4.incomingSubject);
//        channelSet4.incomingSubject.onNext(data2.get(data2.size() - 1));
//
//        Thread.sleep(500);  // let the registry run as it's on a different loop
//
//        verifyRegistryContentSourceEntries(registry, channel1.getSource(), data1Size);
//        verifyRegistryContentSourceEntries(registry, channel2.getSource(), 0);
//        verifyRegistryContentSourceEntries(registry, channel3.getSource(), 0);
//        verifyRegistryContentSourceEntries(registry, channel4.getSource(), data2Size - 2);  // 2 less this round
    }
}
