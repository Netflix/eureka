package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.SnapshotComplete;
import com.netflix.eureka2.protocol.discovery.SnapshotRegistration;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.metric.InterestChannelMetrics;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class InterestChannelImplTest {

    public static final Interest<InstanceInfo> SNAPSHOT_INTEREST = Interests.forFullRegistry();

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final InterestChannelMetrics metrics = mock(InterestChannelMetrics.class);

    private final MessageConnection connection = mock(MessageConnection.class);
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();

    private InterestChannelImpl interestChannel;

    @Before
    public void setUp() throws Exception {
        when(connection.incoming()).thenReturn(incomingSubject);
        when(connection.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(connection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(connection.acknowledge()).thenReturn(Observable.<Void>empty());

        interestChannel = new InterestChannelImpl(registry, connection, metrics);
    }

    @Test
    public void testSnapshotSubscription() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        when(registry.forSnapshot(any(Interest.class))).thenReturn(Observable.just(instanceInfo));

        // Send snapshot registration
        incomingSubject.onNext(new SnapshotRegistration(SNAPSHOT_INTEREST));

        // Verify registry content is streamed back
        verify(connection, times(1)).acknowledge();
        verify(connection, times(1)).submitWithAck(new AddInstance(instanceInfo));
        verify(connection, times(1)).submit(SnapshotComplete.INSTANCE);
    }
}