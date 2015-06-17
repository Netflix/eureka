package com.netflix.eureka2.integration.batching;

import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistryImpl;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.protocol.interest.InterestSetNotification;
import com.netflix.eureka2.protocol.interest.SampleAddInstance;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.transport.tcp.replication.TestTcpReplicationHandler;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.subjects.ReplaySubject;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class ReplicationBatchHintsIntegrationTest extends AbstractBatchHintsIntegrationTest {

    private ReplaySubject<Object> incomingSubject1;
    private ReplaySubject<Void> serverConnection1Lifecycle;
    private MessageConnection connection1;

    private ReplaySubject<Object> incomingSubject2;
    private ReplaySubject<Void> serverConnection2Lifecycle;
    private MessageConnection connection2;

    private SourcedEurekaRegistryImpl registry;

    private TestTcpReplicationHandler handler;

    @Before
    public void setUp() {
        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
        IndexRegistry<InstanceInfo> localIndexRegistry = new IndexRegistryImpl<>();
        IndexRegistry<InstanceInfo> compositeIndexRegistry = new BatchAwareIndexRegistry<>(localIndexRegistry, remoteBatchingRegistry);

        registry = spy(new SourcedEurekaRegistryImpl(compositeIndexRegistry, EurekaRegistryMetricFactory.registryMetrics()));

        incomingSubject1 = ReplaySubject.create();
        serverConnection1Lifecycle = ReplaySubject.create();

        connection1 = mock(MessageConnection.class);
        when(connection1.incoming()).thenReturn(incomingSubject1);
        when(connection1.submit(any(ReplicationHelloReply.class))).thenReturn(Observable.<Void>empty());
        when(connection1.acknowledge()).thenReturn(Observable.<Void>empty());
        when(connection1.lifecycleObservable()).thenReturn(serverConnection1Lifecycle);

        incomingSubject2 = ReplaySubject.create();
        serverConnection2Lifecycle = ReplaySubject.create();

        connection2 = mock(MessageConnection.class);
        when(connection2.incoming()).thenReturn(incomingSubject2);
        when(connection2.submit(any(ReplicationHelloReply.class))).thenReturn(Observable.<Void>empty());
        when(connection2.acknowledge()).thenReturn(Observable.<Void>empty());
        when(connection2.lifecycleObservable()).thenReturn(serverConnection2Lifecycle);

        SelfInfoResolver selfInfoResolver = mock(SelfInfoResolver.class);
        when(selfInfoResolver.resolve()).thenReturn(Observable.just(SampleInstanceInfo.CliServer.build()));

        handler = new TestTcpReplicationHandler(
                WriteServerConfig.writeBuilder().build(),
                selfInfoResolver,
                registry,
                WriteServerMetricFactory.writeServerMetrics()
        );
    }

    @After
    public void tearDown() {
    }


    @Test
    public void testReceiverReplicationChannelChangeEvictionOnBufferHints() throws Exception {
        final Interest<InstanceInfo> interest = Interests.forFullRegistry();

        final List<InterestSetNotification> data1 = Arrays.asList(
                newBufferStart(interest),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                newBufferEnd(interest)
        );

        final Observable<InterestSetNotification> remoteData = Observable.from(data1);

        ReceiverReplicationChannel channel1 = handler.doHandle(connection1);
        channel1.asLifecycleObservable().subscribe();

        incomingSubject1.onNext(new ReplicationHello("remoteServer", data1.size() - 2));
        remoteData.concatWith(Observable.<InterestSetNotification>never()).subscribe(incomingSubject1);

        Thread.sleep(500);  // let the registry run as it's on a different loop

        verifyRegistryContentContainOnlySource(registry, channel1.getSource());

        channel1.close();
        ReceiverReplicationChannel channel2 = handler.doHandle(connection2);
        channel2.asLifecycleObservable().subscribe();

        incomingSubject2.onNext(new ReplicationHello("remoteServer", data1.size() - 2));
        remoteData.concatWith(Observable.<InterestSetNotification>never()).subscribe(incomingSubject2);

        Thread.sleep(500);  // let the registry run as it's on a different loop

        verifyRegistryContentContainOnlySource(registry, channel2.getSource());
    }
}
