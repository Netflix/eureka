package com.netflix.eureka2.client.channel;

import java.util.Collections;

import com.netflix.eureka2.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.SnapshotComplete;
import com.netflix.eureka2.protocol.discovery.SnapshotRegistration;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class SnapshotInterestChannelImplTest {

    public static final Interest<InstanceInfo> SNAPSHOT_INTEREST = Interests.forFullRegistry();

    private final TcpDiscoveryClient transportClient = mock(TcpDiscoveryClient.class);

    private final MessageConnection connection = mock(MessageConnection.class);
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final PublishSubject<Void> ackReply = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(transportClient.connect()).thenReturn(Observable.just(connection));
        when(connection.incoming()).thenReturn(incomingSubject);
        when(connection.submitWithAck(anyObject())).thenReturn(ackReply);
        when(connection.acknowledge()).thenReturn(Observable.<Void>empty());
    }

    @Test
    public void testSubscribeHandlesNotificationsAndDisconnects() {
        TestSubscriber<InstanceInfo> testSubscriber = new TestSubscriber<>();
        SnapshotInterestChannelImpl interestChannel = new SnapshotInterestChannelImpl(transportClient);

        interestChannel.forSnapshot(SNAPSHOT_INTEREST).subscribe(testSubscriber);
        ackReply.onCompleted();

        // Verify subscribes properly
        verify(transportClient, times(1)).connect();
        verify(connection, times(1)).submitWithAck(new SnapshotRegistration(SNAPSHOT_INTEREST));

        // Verify handles incoming notifications
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        incomingSubject.onNext(new AddInstance(instanceInfo));

        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(Collections.singletonList(instanceInfo));

        // Verify disconnects once transport completes
        incomingSubject.onNext(SnapshotComplete.INSTANCE);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
    }
}