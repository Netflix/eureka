package com.netflix.eureka2.server.transport.tcp.replication;

import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.protocol.interest.StreamStateUpdate;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.interests.Interests.*;
import static com.netflix.eureka2.interests.StreamStateNotification.BufferState.*;
import static com.netflix.eureka2.registry.Source.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class TcpReplicationHandlerTest {

    private SourcedEurekaRegistry<InstanceInfo> registry;
    private TcpReplicationHandler handler;

    private SelfInfoResolver selfInfoResolver;
    private MessageConnection connection1;
    private MessageConnection connection2;
    private ReplaySubject<Object> inputSubject1 = ReplaySubject.create();
    private ReplaySubject<Object> inputSubject2 = ReplaySubject.create();


    @BeforeClass
    public static void setUpClass() {
        System.setProperty("eureka.hacks.receiverReplicationChannel.bufferHintDelayMs", "0");
        System.setProperty("eureka.hacks.receiverReplicationChannel.maxBufferHintDelayMs", "0");
    }

    @AfterClass
    public static void tearDownClass() {
        System.clearProperty("eureka.hacks.receiverReplicationChannel.bufferHintDelayMs");
        System.clearProperty("eureka.hacks.receiverReplicationChannel.maxBufferHintDelayMs");
    }

    @Before
    public void setUp() {
        registry = mock(SourcedEurekaRegistry.class);
        connection1 = mock(MessageConnection.class);
        when(connection1.incoming()).thenReturn(inputSubject1);

        connection2 = mock(MessageConnection.class);
        when(connection2.incoming()).thenReturn(inputSubject2);

        selfInfoResolver = mock(SelfInfoResolver.class);
        when(selfInfoResolver.resolve()).thenReturn(Observable.just(SampleInstanceInfo.CliServer.build()));

        handler = new TcpReplicationHandler(
                WriteServerConfig.writeBuilder().build(),
                selfInfoResolver,
                registry,
                WriteServerMetricFactory.writeServerMetrics()
        );
    }

    @Test
    public void testDoHandleEvictOlderChannelData() throws Exception {
        ReceiverReplicationChannel channel1 = handler.doHandle(connection1);
        channel1.hello(new ReplicationHello("remoteServer", 0));  // do this to the set the channel source
        verify(registry, never()).evictAll(any(SourceMatcher.class));

        ReceiverReplicationChannel channel2 = handler.doHandle(connection2);
        channel2.hello(new ReplicationHello("remoteServer", 0));  // do this to the set the channel source
        verify(registry, never()).evictAll(any(SourceMatcher.class));

        ArgumentCaptor<SourceMatcher> argument = ArgumentCaptor.forClass(SourceMatcher.class);

        // now close channel 1 and propagate a stream state on channel 2, should trigger the eviction
        channel1.close();
        inputSubject2.onNext(new StreamStateUpdate(new StreamStateNotification<>(BufferStart, forFullRegistry())));
        verify(registry, never()).evictAll(any(SourceMatcher.class));

        inputSubject2.onNext(new StreamStateUpdate(new StreamStateNotification<>(BufferEnd, forFullRegistry())));
        Thread.sleep(50);  // sleep a bit as it is async
        verify(registry).evictAll(argument.capture());

        // need string compare for source matcher
        assertThat(argument.getValue().toString(), is(matcherFor(channel1.getSource()).toString()));
    }
}
