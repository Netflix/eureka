package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.interests.StreamStateNotification.BufferingState;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class BatchingRegistryImplTest {

    private static final Interest<InstanceInfo> ATOMIC_INTEREST_A = Interests.forApplications("A");
    private static final Interest<InstanceInfo> ATOMIC_INTEREST_B = Interests.forApplications("B");
    private static final Interest<InstanceInfo> INTEREST_AB = Interests.forSome(ATOMIC_INTEREST_A, ATOMIC_INTEREST_B);

    private final BatchingRegistryImpl<InstanceInfo> batchingRegistry = new BatchingRegistryImpl<>();
    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        batchingRegistry.subscribe(notificationSubject);
    }

    @Test
    public void testGeneratesBatchStatusUpdatesFromChannelNotificationStream() throws Exception {
        ExtTestSubscriber<BufferingState> interestSubscriberA = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriberA);
        ExtTestSubscriber<BufferingState> interestSubscriberAB = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(INTEREST_AB).subscribe(interestSubscriberAB);

        // No notification was sent, so the state is unknown
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferingState.Unknown)));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferingState.Unknown)));

        // Generate batching markers in the stream
        notificationSubject.onNext(StreamStateNotification.bufferNotification(ATOMIC_INTEREST_A));
        notificationSubject.onNext(StreamStateNotification.bufferNotification(ATOMIC_INTEREST_B));
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferingState.Buffer)));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferingState.Buffer)));

        // Finish interest A
        notificationSubject.onNext(StreamStateNotification.finishBufferingNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferingState.FinishBuffering)));
        assertThat(interestSubscriberAB.takeNext(), is(nullValue()));

        // Finish interest B
        notificationSubject.onNext(StreamStateNotification.finishBufferingNotification(ATOMIC_INTEREST_B));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferingState.FinishBuffering)));
    }

    @Test
    public void testResetsStateAfterChannelReconnect() throws Exception {
        ExtTestSubscriber<BufferingState> interestSubscriber = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriber);

        // No notification was sent, so the state is unknown
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferingState.Unknown)));

        // Signal batching begin
        notificationSubject.onNext(StreamStateNotification.bufferNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferingState.Buffer)));

        // Sending error on channel should trigger FinishBuffering
        notificationSubject.onError(new Exception("error"));
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferingState.FinishBuffering)));

        // Connect new channel and send some data
        PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject2 = PublishSubject.create();
        batchingRegistry.subscribe(notificationSubject2);

        notificationSubject2.onNext(StreamStateNotification.bufferNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferingState.Buffer)));
    }
}