package com.netflix.eureka2.client.interest;

import java.util.List;

import com.netflix.eureka2.interest.TestInterestSubscriber;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Index.InitStateHolder;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class BatchAwareIndexRegistryTest {

    private static final Interest<InstanceInfo> ATOMIC_INTEREST_A = Interests.forApplications("A");
    private static final Interest<InstanceInfo> ATOMIC_INTEREST_B = Interests.forApplications("B");
    private static final Interest<InstanceInfo> INTEREST_AB = Interests.forSome(ATOMIC_INTEREST_A, ATOMIC_INTEREST_B);

    private static final ChangeNotification<InstanceInfo> DATA_NOTIFICATION = SampleChangeNotification.DiscoveryAdd.newNotification();

    private final BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
    private PublishSubject<ChangeNotification<InstanceInfo>> remoteBatchingHintsSubject = PublishSubject.create();

    private final IndexRegistry<InstanceInfo> delegateIndexRegistry = mock(IndexRegistry.class);
    private final BatchAwareIndexRegistry<InstanceInfo> indexRegistry =
            new BatchAwareIndexRegistry<>(delegateIndexRegistry, remoteBatchingRegistry);

    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject = PublishSubject.create();

    private final TestInterestSubscriber testSubscriber = new TestInterestSubscriber();

    @Before
    public void setUp() throws Exception {
        remoteBatchingRegistry.connectTo(remoteBatchingHintsSubject);

        when(delegateIndexRegistry.forInterest(any(Interest.class), any(Observable.class), any(InitStateHolder.class)))
                .thenReturn(notificationSubject);
    }

    /**
     * Test situation where we subscribe for the first time for a particular
     * interest, and there are two data items available in the server.
     * <p>
     * Expected behavior: two data notification followed by single finish buffering
     */
    @Test
    public void testColdCacheAndDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));
        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);

        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test situation where we subscribe second time to the same interest (2 items in cache), and there are
     * no more data items available in the server.
     * <p>
     * Expected behavior: two data notification followed by single finish buffering
     */
    @Test
    public void testHotCacheAndNoDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));
        sendLocalBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);

        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test situation where we subscribe second time to the same interest (2 items in cache), after the
     * original subscriber disconnected. There are two fresh data items on the server.
     * <p>
     * Expected behavior: four data notification followed by single buffer end notification
     */
    @Test
    public void testStaleCacheAndDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        sendLocalBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION, DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test situation where there was a reconnect on the channel within a single client subscription.
     * <p>
     * Expected behavior: two data notification followed by single finish buffering (from cache), followed by
     * two data notification followed by single finish buffering (from server)
     */
    @Test
    public void testNoCacheAndTwoReconnectsWithDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        // First time local registry was empty
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));
        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        // Simulate disconnect
        reconnect();

        // Next time it contains the same data as we receive from the channel
        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        // Another subscriber should get single batch from cache
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber2 = new ExtTestSubscriber<>();
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber2);

        sendLocalBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);

        List<ChangeNotification<InstanceInfo>> notifications = testSubscriber2.takeNext(3);
        assertThat(notifications.get(2).getKind(), is(equalTo(Kind.BufferSentinel)));
    }

    /**
     * Test composite subscription scenario, with no data in local registry and two data items on server
     * per each vip (A & B).
     * <p>
     * Expected behavior: single batch of four items expected
     *
     * Note: in current Eureka registry implementation, where atomic interests are replayed
     * one after another and merged in the index, we will get two batches.
     */
    @Test
    public void testCompositeWithColdCacheAndDataInChannel() throws Exception {
        indexRegistry.forInterest(INTEREST_AB, null, null).subscribe(testSubscriber);

        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_B));

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        sendRemoteBatch(ATOMIC_INTEREST_B, DATA_NOTIFICATION, DATA_NOTIFICATION);
        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Generate BufferStart hints at the same time from two sources. There are two items in
     * cache, and two on the server.
     * <p>
     * Expected behavior: single batch with four elements
     */
    @Test
    public void testOverlappingBufferHintsFromCacheAndChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        // Mark BatchStart
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));
        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferStartNotification(ATOMIC_INTEREST_A));

        // Send data from cache followed by BufferEnd marker
        sendData(DATA_NOTIFICATION, DATA_NOTIFICATION);
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));

        testSubscriber.assertReceives(DATA_NOTIFICATION, DATA_NOTIFICATION);
        assertThat(testSubscriber.takeNext(), is(nullValue())); // Check sentinel was not sent

        // Send data from server followed by BufferEnd marker
        sendData(DATA_NOTIFICATION, DATA_NOTIFICATION);
        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferEndNotification(ATOMIC_INTEREST_A));

        testSubscriber.assertReceivesBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    @SafeVarargs
    private final void sendLocalBatch(Interest<InstanceInfo> interest, ChangeNotification<InstanceInfo>... dataNotifications) {
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(interest));
        sendData(dataNotifications);
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(interest));
    }

    @SafeVarargs
    private final void sendRemoteBatch(Interest<InstanceInfo> interest, ChangeNotification<InstanceInfo>... dataNotifications) {
        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferStartNotification(interest));
        sendData(dataNotifications);
        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferEndNotification(interest));
    }

    @SafeVarargs
    private final void sendData(ChangeNotification<InstanceInfo>... dataNotifications) {
        for (ChangeNotification<InstanceInfo> n : dataNotifications) {
            notificationSubject.onNext(n);
        }
    }

    private void reconnect() {
        remoteBatchingHintsSubject.onCompleted();
        remoteBatchingHintsSubject = PublishSubject.create();
        remoteBatchingRegistry.connectTo(remoteBatchingHintsSubject);
    }
}