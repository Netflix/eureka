package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
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
        batchingRegistry.connectTo(notificationSubject);
    }

    @Test
    public void testGeneratesBatchStatusUpdatesFromChannelNotificationStream() throws Exception {
        ExtTestSubscriber<BufferState> interestSubscriberA = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriberA);
        ExtTestSubscriber<BufferState> interestSubscriberAB = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(INTEREST_AB).subscribe(interestSubscriberAB);

        // No notification was sent, so the state is unknown
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferState.Unknown)));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferState.Unknown)));

        // Generate batching markers in the stream
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_B));
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferState.BufferStart)));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferState.BufferStart)));

        // Finish interest A
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriberA.takeNext(), is(equalTo(BufferState.BufferEnd)));
        assertThat(interestSubscriberAB.takeNext(), is(nullValue()));

        // Finish interest B
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_B));
        assertThat(interestSubscriberAB.takeNext(), is(equalTo(BufferState.BufferEnd)));
    }

    @Test
    public void testResetsStateAfterChannelReconnectOnCompleted() throws Exception {
        doTestResetsStateAfterChannelReconnect(false);
    }

    @Test
    public void testResetsStateAfterChannelReconnectOnError() throws Exception {
        doTestResetsStateAfterChannelReconnect(true);
    }

    private void doTestResetsStateAfterChannelReconnect(boolean failNotificationChannel) {
        ExtTestSubscriber<BufferState> interestSubscriber = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriber);

        // No notification was sent, so the state is unknown
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferState.Unknown)));

        // Signal batching begin
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferState.BufferStart)));

        // Sending error on channel should trigger BufferEnd
        if(failNotificationChannel) {
            notificationSubject.onError(new Exception("error"));
        } else {
            notificationSubject.onCompleted();
        }
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferState.BufferEnd)));

        // Connect new channel and send some data
        PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject2 = PublishSubject.create();
        batchingRegistry.connectTo(notificationSubject2);

        notificationSubject2.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));
        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferState.BufferStart)));
    }

    @Test
    public void testSubscriptionChangeCleansUpInternalState() throws Exception {
        // Signal batching begin
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));

        ExtTestSubscriber<BufferState> interestSubscriber = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriber);

        assertThat(interestSubscriber.takeNext(), is(equalTo(BufferState.BufferStart)));

        // Now update the active subscription
        batchingRegistry.retainAll(ATOMIC_INTEREST_B);

        ExtTestSubscriber<BufferState> secondInterestSubscriber = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(secondInterestSubscriber);

        assertThat(secondInterestSubscriber.takeNext(), is(equalTo(BufferState.Unknown)));
    }

    @Test
    public void testShutdownReleasesResources() throws Exception {
        ExtTestSubscriber<BufferState> interestSubscriberA = new ExtTestSubscriber<>();
        batchingRegistry.forInterest(ATOMIC_INTEREST_A).subscribe(interestSubscriberA);

        batchingRegistry.shutdown();
        interestSubscriberA.assertOnCompleted();
    }
}