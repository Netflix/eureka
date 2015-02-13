package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics.AtomicInterest;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.StreamStateUpdate;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class InterestChannelImplTest {

    public static final Interest<InstanceInfo> CLIENT_INTEREST = Interests.forFullRegistry();

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final ServerInterestChannelMetrics interestChannelMetrics = mock(ServerInterestChannelMetrics.class);

    private final MessageConnection connection = mock(MessageConnection.class);
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();

    private InterestChannelImpl channel;
    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(connection.incoming()).thenReturn(incomingSubject);
        when(connection.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(connection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(connection.acknowledge()).thenReturn(Observable.<Void>empty());
        when(connection.onCompleted()).thenReturn(Observable.<Void>empty());

        when(registry.forInterest(any(Interest.class))).thenReturn(notificationSubject);

        channel = new InterestChannelImpl(registry, connection, interestChannelMetrics);
    }

    @Test
    public void testStreamStateNotificationsAreSentToClients() throws Exception {
        // Send interest subscription first
        incomingSubject.onNext(new InterestRegistration(CLIENT_INTEREST));

        // Trigger buffer state change notification
        StreamStateNotification<InstanceInfo> stateNotification =
                StreamStateNotification.bufferNotification(CLIENT_INTEREST);

        notificationSubject.onNext(stateNotification);
        verify(connection, times(1)).submitWithAck(new StreamStateUpdate(stateNotification));

        // Trigger finishBuffering state change notification
        stateNotification = StreamStateNotification.finishBufferingNotification(CLIENT_INTEREST);

        notificationSubject.onNext(stateNotification);
        verify(connection, times(1)).submitWithAck(new StreamStateUpdate(stateNotification));
    }

    @Test(timeout = 60000)
    public void testMetricsStateMonitoring() throws Exception {
        verifyMetricStateMonitoring(new InterestRegistration(Interests.forFullRegistry()));
    }

    private <S> void verifyMetricStateMonitoring(S subscriptionRequest) {
        incomingSubject.onNext(subscriptionRequest);

        verify(interestChannelMetrics, times(1)).incrementStateCounter(STATE.Open);

        // Shutdown channel
        channel.close();
        verify(interestChannelMetrics, times(1)).decrementStateCounter(STATE.Open);
        verify(interestChannelMetrics, times(1)).incrementStateCounter(STATE.Closed);
    }

    @Test(timeout = 60000)
    public void testNotificationMetrics() throws Exception {
        // Simulate interest subscription
        incomingSubject.onNext(new InterestRegistration(Interests.forFullRegistry()));

        // Send change notifications
        InstanceInfo info = SampleInstanceInfo.DiscoveryServer.build();
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, info));

        verify(interestChannelMetrics, times(1)).incrementApplicationNotificationCounter(info.getApp());
    }

    @Test(timeout = 60000)
    public void testSubscriptionMetrics() throws Exception {
        // Full interest subscription
        incomingSubject.onNext(new InterestRegistration(Interests.forFullRegistry()));
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.InterestAll, null);

        // Swap with application interest
        Interest<InstanceInfo> appInterest = Interests.forApplications("someApp");
        incomingSubject.onNext(new InterestRegistration(appInterest));
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Application, "someApp");
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.InterestAll, null);

        // Swap with vip interest
        Interest<InstanceInfo> vipInterest = Interests.forVips("someVip");
        incomingSubject.onNext(new InterestRegistration(vipInterest));
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Vip, "someVip");
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Application, "someApp");

        // Swap with instance interest
        Interest<InstanceInfo> instanceInterest = Interests.forInstance(Operator.Equals, "someInstance");
        incomingSubject.onNext(new InterestRegistration(instanceInterest));
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Instance, "someInstance");
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Vip, "someVip");

        incomingSubject.onNext(new InterestRegistration(Interests.forNone()));
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Instance, "someInstance");

        // Swap with a composite of everything
        reset(interestChannelMetrics);

        Interest<InstanceInfo> compositeInterest = Interests.forSome(appInterest, vipInterest, instanceInterest);
        incomingSubject.onNext(new InterestRegistration(compositeInterest));

        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Application, "someApp");
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Vip, "someVip");
        verify(interestChannelMetrics, times(1)).incrementSubscriptionCounter(AtomicInterest.Instance, "someInstance");

        incomingSubject.onNext(new InterestRegistration(Interests.forNone()));

        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Application, "someApp");
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Vip, "someVip");
        verify(interestChannelMetrics, times(1)).decrementSubscriptionCounter(AtomicInterest.Instance, "someInstance");
    }
}