package com.netflix.eureka2.client.interest;

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
    private final PublishSubject<ChangeNotification<InstanceInfo>> remoteBatchingHintsSubject = PublishSubject.create();

    private final IndexRegistry<InstanceInfo> delegateIndexRegistry = mock(IndexRegistry.class);
    private final BatchAwareIndexRegistry<InstanceInfo> indexRegistry =
            new BatchAwareIndexRegistry<>(delegateIndexRegistry, remoteBatchingRegistry);

    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject = PublishSubject.create();

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        remoteBatchingRegistry.subscribe(remoteBatchingHintsSubject);

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

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
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
        sendLocalBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test situation where we subscribe second time to the same interest (2 items in cache), after the
     * original subscriber disconnected (cold cache). There are two fresh data items on the server.
     * <p>
     * Expected behavior: two data notification followed by single finish buffering (from cache), followed by
     * two data notification followed by single finish buffering (from server)
     */
    @Test
    public void testStaleCacheAndDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        sendLocalBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test situation where we subscribe second time to the same interest (2 items in cache), after the
     * original subscriber disconnected (cold cache). There are two fresh data items on the server.
     * <p>
     * Expected behavior: two data notification followed by single finish buffering (from cache), followed by
     * two data notification followed by single finish buffering (from server)
     */
    @Test
    public void testNoCacheAndTwoReconnectsWithDataInChannel() throws Exception {
        indexRegistry.forInterest(ATOMIC_INTEREST_A, null, null).subscribe(testSubscriber);

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    /**
     * Test composite subscription scenario, with no data on server and two data items on server
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

        notificationSubject.onNext(StreamStateNotification.bufferNotification(INTEREST_AB));

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);

        sendRemoteBatch(ATOMIC_INTEREST_A, DATA_NOTIFICATION, DATA_NOTIFICATION);
        verifyReceivedBatch(DATA_NOTIFICATION, DATA_NOTIFICATION);
    }

    @SafeVarargs
    private final void sendLocalBatch(Interest<InstanceInfo> interest, ChangeNotification<InstanceInfo>... dataNotifications) {
        notificationSubject.onNext(StreamStateNotification.bufferNotification(interest));
        for (ChangeNotification<InstanceInfo> n : dataNotifications) {
            notificationSubject.onNext(n);
        }
        notificationSubject.onNext(StreamStateNotification.finishBufferingNotification(interest));
    }

    @SafeVarargs
    private final void sendRemoteBatch(Interest<InstanceInfo> interest, ChangeNotification<InstanceInfo>... dataNotifications) {
        remoteBatchingHintsSubject.onNext(StreamStateNotification.bufferNotification(interest));
        for (ChangeNotification<InstanceInfo> n : dataNotifications) {
            notificationSubject.onNext(n);
        }
        remoteBatchingHintsSubject.onNext(StreamStateNotification.finishBufferingNotification(interest));
    }

    @SafeVarargs
    private final void verifyReceivedBatch(ChangeNotification<InstanceInfo>... dataNotifications) {
        for (ChangeNotification<InstanceInfo> n : dataNotifications) {
            assertThat(testSubscriber.takeNextOrFail(), is(equalTo(n)));
        }
        assertThat(testSubscriber.takeNextOrFail().getKind(), is(equalTo(Kind.BufferingSentinel)));
    }
}