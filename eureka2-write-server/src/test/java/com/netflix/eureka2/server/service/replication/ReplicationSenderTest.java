package com.netflix.eureka2.server.service.replication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.channel.TestChannelFactory;
import com.netflix.eureka2.channel.TestSenderReplicationChannel;
import com.netflix.eureka2.channel.TestSenderReplicationChannel.ReplicationItem;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.SenderReplicationChannel;
import com.netflix.eureka2.server.channel.SenderReplicationChannelFactory;
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
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ReplicationSenderTest {

    private static final int RETRY_WAIT_MILLIS = 10;

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.DiscoveryServer.build();
    private static final InstanceInfo REMOTE_INFO = SampleInstanceInfo.DiscoveryServer.build();
    private static final Source REPLICATION_SOURCE = new Source(Source.Origin.REPLICATED, REMOTE_INFO.getId(), 0);
    private static final ReplicationHelloReply HELLO_REPLY = new ReplicationHelloReply(REPLICATION_SOURCE, true);
    private ReplicationHello hello;

    private final AtomicInteger channelId = new AtomicInteger(0);

    private final TestScheduler testScheduler = Schedulers.test();

    private final SourcedEurekaRegistry<InstanceInfo> registry =
            new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics(), testScheduler);
    private final Source localSource = new Source(Source.Origin.LOCAL);

    private List<InstanceInfo> registryContent;
    private List<String> registryIds;

    private ChannelFactory<ReplicationChannel> mockFactory;
    private TestChannelFactory<ReplicationChannel> factory;
    private ReplicationSender handler;

    @Before
    public void setUp() throws Exception {
        registryContent = Arrays.asList(
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.CliServer.build(),
                SampleInstanceInfo.CliServer.build()
        );
        registryIds = new ArrayList<>();
        for (InstanceInfo instanceInfo : registryContent) {
            registryIds.add(instanceInfo.getId());
        }

        mockFactory = mock(SenderReplicationChannelFactory.class);
        factory = new TestChannelFactory<>(mockFactory);
        handler = spy(new ReplicationSenderImpl(factory, RETRY_WAIT_MILLIS, registry, SELF_INFO, testScheduler));

        for (InstanceInfo instanceInfo : registryContent) {
            registry.register(instanceInfo, localSource).subscribe();
            testScheduler.triggerActions();
        }

        Source senderSource = new Source(Source.Origin.REPLICATED, SELF_INFO.getId(), 0);
        hello = new ReplicationHello(senderSource, registry.size());
    }

    @After
    public void tearDown() throws Exception {
        handler.shutdown();
        registry.shutdown();
    }

    @Test
    public void testDisconnectsWhenConnectedToItself() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                MessageConnection messageConnection = mock(MessageConnection.class);
                when(messageConnection.submit(hello)).thenReturn(Observable.<Void>empty());
                when(messageConnection.incoming()).thenReturn(Observable.<Object>just(new ReplicationHelloReply(REPLICATION_SOURCE, true)));

                TransportClient transportClient = mock(TransportClient.class);
                when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

                ReplicationChannel channel = new SenderReplicationChannel(transportClient, mock(ReplicationChannelMetrics.class));

                return new TestSenderReplicationChannel(channel, channelId.getAndIncrement());
            }
        });

        handler.startReplication();  // internally auto subscribes

        TestSenderReplicationChannel testChannel;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestSenderReplicationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.closeCalled, is(true));
        assertThat(testChannel.operations.size(), is(1));
        assertThat(testChannel.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));
        assertThat(testChannel.replicationItems.size(), is(0));

        registry.unregister(registryContent.get(0), localSource).subscribe();
        testScheduler.triggerActions();

        registry.unregister(registryContent.get(1), localSource).subscribe();
        testScheduler.triggerActions();

        assertThat(testChannel.replicationItems.size(), is(0));
    }

    @Test
    public void testReplicationLifecycle() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        handler.startReplication();  // internally auto subscribes

        TestSenderReplicationChannel testChannel;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestSenderReplicationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(1));
        assertThat(testChannel.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));
        assertThat(testChannel.replicationItems.size(), is(5));

        List<String> ids = new ArrayList<>();
        for (ReplicationItem item : testChannel.replicationItems) {
            assertThat(item.type, is(ReplicationItem.Type.Register));
            ids.add(item.id);
        }

        assertThat(ids, containsInAnyOrder(registryIds.toArray()));

        registry.unregister(registryContent.get(0), localSource).subscribe();
        testScheduler.triggerActions();

        registry.unregister(registryContent.get(1), localSource).subscribe();
        testScheduler.triggerActions();

        assertThat(testChannel.replicationItems.size(), is(7));

        List<String> unregisterIds = new ArrayList<>();
        for (ReplicationItem item : testChannel.replicationItems) {
            if (item.type == ReplicationItem.Type.Unregister) {
                unregisterIds.add(item.id);
            }
        }

        assertThat(unregisterIds, containsInAnyOrder(registryContent.get(0).getId(), registryContent.get(1).getId()));
    }

    @Test
    public void testReplicationLifecycleWithHelloFailure() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newFailAtHelloChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        doFailoverTest();
    }

    @Test
    public void testReplicationLifecycleWithReplicationFailure() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newFailAtReplicateChannel(channelId.getAndIncrement());
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        doFailoverTest();
    }

    private void doFailoverTest() throws Exception {

        handler.startReplication();  // internally auto subscribes

        testScheduler.advanceTimeBy(RETRY_WAIT_MILLIS, TimeUnit.MILLISECONDS);

        assertThat(factory.getAllChannels().size(), is(2));
        TestSenderReplicationChannel testChannel0 = (TestSenderReplicationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));
        assertThat(testChannel0.replicationItems.size(), is(0));

        TestSenderReplicationChannel testChannel1 = (TestSenderReplicationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(1));
        assertThat(testChannel1.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));

        assertThat(testChannel1.replicationItems.size(), is(5));

        List<String> ids = new ArrayList<>();
        for (ReplicationItem item : testChannel1.replicationItems) {
            assertThat(item.type, is(ReplicationItem.Type.Register));
            ids.add(item.id);
        }

        assertThat(ids, containsInAnyOrder(registryIds.toArray()));
    }

    // note this is different to the above test as the error in this case come from the channel without any operation
    // also, this test should show that on a channel disconnect, the replication channel should resend all registry info`
    @Test
    public void testRetryOnChannelDisconnectError() throws Exception {
        final int failTimeMillis = 100;
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                if (channelId.get() == 0) {
                    return newTimedFailChannel(channelId.getAndIncrement(), failTimeMillis);
                }
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        handler.startReplication();  // internally auto subscribes

        testScheduler.advanceTimeBy(RETRY_WAIT_MILLIS + failTimeMillis, TimeUnit.MILLISECONDS);

        assertThat(factory.getAllChannels().size(), is(2));
        TestSenderReplicationChannel testChannel0 = (TestSenderReplicationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel0.id, is(0));
        assertThat(testChannel0.closeCalled, is(true));
        assertThat(testChannel0.operations.size(), is(1));
        assertThat(testChannel0.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));

        assertThat(testChannel0.replicationItems.size(), is(5));

        TestSenderReplicationChannel testChannel1 = (TestSenderReplicationChannel) factory.getAllChannels().get(1);
        assertThat(testChannel1.id, is(1));
        assertThat(testChannel1.closeCalled, is(false));
        assertThat(testChannel1.operations.size(), is(1));
        assertThat(testChannel1.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));

        assertThat(testChannel1.replicationItems.size(), is(5));

        List<String> ids = new ArrayList<>();
        for (ReplicationItem item : testChannel1.replicationItems) {
            assertThat(item.type, is(ReplicationItem.Type.Register));
            ids.add(item.id);
        }

        assertThat(ids, containsInAnyOrder(registryIds.toArray()));
    }

    @Test
    public void testShutdownCleanUpResources() throws Exception {
        when(mockFactory.newChannel()).then(new Answer<TestSenderReplicationChannel>() {
            @Override
            public TestSenderReplicationChannel answer(InvocationOnMock invocation) throws Throwable {
                return newAlwaysSuccessChannel(channelId.getAndIncrement());
            }
        });

        handler.startReplication();  // internally auto subscribes

        TestSenderReplicationChannel testChannel;
        assertThat(factory.getAllChannels().size(), is(1));
        testChannel = (TestSenderReplicationChannel) factory.getAllChannels().get(0);
        assertThat(testChannel.id, is(0));
        assertThat(testChannel.closeCalled, is(false));
        assertThat(testChannel.operations.size(), is(1));
        assertThat(testChannel.operations.iterator().next().getSource().getName(), equalTo(hello.getSource().getName()));
        assertThat(testChannel.replicationItems.size(), is(5));

        List<String> ids = new ArrayList<>();
        for (ReplicationItem item : testChannel.replicationItems) {
            assertThat(item.type, is(ReplicationItem.Type.Register));
            ids.add(item.id);
        }

        assertThat(ids, containsInAnyOrder(registryIds.toArray()));

        handler.shutdown();

        verify(handler, times(1)).shutdown();
        assertThat(testChannel.closeCalled, is(true));

        // do some more registers, they should not go to the channel
        registry.register(SampleInstanceInfo.ZuulServer.build(), localSource).subscribe();
        registry.register(SampleInstanceInfo.ZuulServer.build(), localSource).subscribe();
        testScheduler.triggerActions();

        // Verify that no new channel is created
        testScheduler.advanceTimeBy(RETRY_WAIT_MILLIS * 2, TimeUnit.MILLISECONDS);
        assertThat(factory.getAllChannels().size(), is(equalTo(1)));
        assertThat(testChannel.replicationItems.size(), is(5));  // none of the new ones
    }


    private static TestSenderReplicationChannel newAlwaysSuccessChannel(Integer id) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.incoming()).thenReturn(Observable.<Object>just(HELLO_REPLY));

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        ReplicationChannel channel = new SenderReplicationChannel(transportClient, mock(ReplicationChannelMetrics.class));

        return new TestSenderReplicationChannel(channel, id);
    }

    private static TestSenderReplicationChannel newFailAtHelloChannel(Integer id) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.incoming()).thenReturn(Observable.error(new Exception("test: hello reply error")));

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        ReplicationChannel channel = new SenderReplicationChannel(transportClient, mock(ReplicationChannelMetrics.class));

        return new TestSenderReplicationChannel(channel, id);
    }

    private static TestSenderReplicationChannel newFailAtReplicateChannel(Integer id) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submit(any(ReplicationHello.class))).thenReturn(Observable.<Void>empty());
        when(messageConnection.submit(any(AddInstance.class))).thenReturn(Observable.<Void>error(new Exception("test: register error")));
        when(messageConnection.submit(any(DeleteInstance.class))).thenReturn(Observable.<Void>error(new Exception("test: unregister error")));
        when(messageConnection.incoming()).thenReturn(Observable.<Object>just(HELLO_REPLY));

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        ReplicationChannel channel = new SenderReplicationChannel(transportClient, mock(ReplicationChannelMetrics.class));

        return new TestSenderReplicationChannel(channel, id);
    }

    private TestSenderReplicationChannel newTimedFailChannel(Integer id, int failAfterMillis) {
        MessageConnection messageConnection = newMockMessageConnection();
        when(messageConnection.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(messageConnection.incoming()).thenReturn(Observable.<Object>just(HELLO_REPLY));

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.just(messageConnection));

        final ReplicationChannel channel = new SenderReplicationChannel(transportClient, mock(ReplicationChannelMetrics.class));

        Observable.empty().delay(failAfterMillis, TimeUnit.MILLISECONDS, testScheduler).subscribe(new Subscriber<Object>() {
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

        return new TestSenderReplicationChannel(channel, id);
    }

    private static MessageConnection newMockMessageConnection() {
        final ReplaySubject<Void> lifecycleSubject = ReplaySubject.create();
        MessageConnection messageConnection = mock(MessageConnection.class);
        when(messageConnection.lifecycleObservable()).thenReturn(lifecycleSubject);
        return messageConnection;
    }
}
