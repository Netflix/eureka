/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.client.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.PercentageDropEvictionStrategy;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RetryableInterestChannelTest {

    private static final Interest<InstanceInfo> INTEREST = Interests.forApplications("testApp");
    private static final Interest<InstanceInfo> INTEREST2 = Interests.forApplications("testApp2");
    private static final InstanceInfo INFO = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp").build();
    private static final InstanceInfo INFO2 = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp2").build();

    private static final long EVICTION_TIMEOUT_MS = 30*1000;
    private static final int EVICTION_ALLOWED_PERCENTAGE_DROP_THRESHOLD = 50;//%

    private final TestScheduler scheduler = Schedulers.test();

    // we need to have actual delegate channels for close() purposes, and as such, we need to mock the underlying
    // messageConnection and TransportClient
    private final MessageConnection mockConnection = mock(MessageConnection.class);
    private final TransportClient mockClient = mock(TransportClient.class);
    private final ReplaySubject<Void> channelLifecycle1 = ReplaySubject.create();
    private final ReplaySubject<Void> channelLifecycle2 = ReplaySubject.create();

    private ClientInterestChannel interestChannel1;
    private ClientInterestChannel interestChannel2; // separate channel obj for the retry test

    private final Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel> channelFactory = mock(Func1.class);

    private SourcedEurekaRegistry<InstanceInfo> delegateRegistry;
    private PreservableEurekaRegistry registry;
    private EvictionQueue evictionQueue;
    private RetryableInterestChannel retryableInterestChannel;

    @Before
    public void setUp() throws Exception {
        when(mockConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(mockConnection.incoming()).thenReturn(Observable.never());
        when(mockConnection.lifecycleObservable()).thenReturn(ReplaySubject.<Void>create());
        when(mockClient.connect()).thenReturn(Observable.just(mockConnection));

        evictionQueue = new EvictionQueueImpl(EVICTION_TIMEOUT_MS, EurekaRegistryMetricFactory.registryMetrics(), scheduler);
        delegateRegistry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics(), scheduler);
        registry = spy(new PreservableEurekaRegistry(
                delegateRegistry,
                evictionQueue,
                new PercentageDropEvictionStrategy(EVICTION_ALLOWED_PERCENTAGE_DROP_THRESHOLD),
                EurekaRegistryMetricFactory.registryMetrics()));

        interestChannel1 = spy(new InterestChannelImpl(registry, mockClient, EurekaClientMetricFactory.clientMetrics().getInterestChannelMetrics()));
        interestChannel2 = spy(new InterestChannelImpl(registry, mockClient, EurekaClientMetricFactory.clientMetrics().getInterestChannelMetrics())); // separate channel obj for the retry test

        doReturn(channelLifecycle1).when(interestChannel1).asLifecycleObservable();
        doReturn(channelLifecycle2).when(interestChannel2).asLifecycleObservable();

        when(channelFactory.call(registry))
                .thenReturn(interestChannel1)
                .thenReturn(interestChannel2);

        retryableInterestChannel = new RetryableInterestChannel(
                channelFactory, registry, RetryableInterestChannel.DEFAULT_INITIAL_DELAY, scheduler);

        when(interestChannel1.associatedRegistry()).thenReturn(registry);
    }

    @After
    public void shutDown() throws Exception {
        retryableInterestChannel.close();
        registry.shutdown();
    }

    @Test
    public void testForwardsRequestsToDelegate() throws Exception {
        // Append operation
        retryableInterestChannel.appendInterest(INTEREST).subscribe();
        verify(interestChannel1, times(1)).appendInterest(INTEREST);

        // Append operation
        retryableInterestChannel.removeInterest(INTEREST).subscribe();
        verify(interestChannel1, times(1)).removeInterest(INTEREST);
    }

    @Test
    public void testCleansUpResources() throws Exception {
        retryableInterestChannel.appendInterest(INTEREST).subscribe();
        verify(interestChannel1, times(1)).appendInterest(INTEREST);

        final CountDownLatch lifecycleLatch = new CountDownLatch(1);
        retryableInterestChannel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                lifecycleLatch.countDown();
            }
            @Override
            public void onError(Throwable e) {}
            @Override
            public void onNext(Void aVoid) {}
        });

        retryableInterestChannel.close();
        assertTrue(lifecycleLatch.await(30, TimeUnit.SECONDS));

        // try again, should onError with ChannelClosedException
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicInteger onCompletedCount = new AtomicInteger(0);
        final AtomicInteger onNextCount = new AtomicInteger(0);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        retryableInterestChannel.appendInterest(INTEREST).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                onCompletedCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable e) {
                errorLatch.countDown();
                errorRef.set(e);
            }

            @Override
            public void onNext(Void aVoid) {
                onNextCount.incrementAndGet();
            }
        });

        assertTrue(errorLatch.await(60, TimeUnit.SECONDS));
        assertThat(errorRef.get(), instanceOf(IllegalStateException.class));
        assertThat(onCompletedCount.get(), equalTo(0));
        assertThat(onNextCount.get(), equalTo(0));
    }

    @Test
    public void testDelegateFailureSendAllRegistryToEvictionQueue() throws Exception {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicBoolean errored = new AtomicBoolean(false);
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();
        registry.forInterest(INTEREST).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completed.set(true);
            }

            @Override
            public void onError(Throwable e) {
                errored.set(true);
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                notifications.add(notification);
            }
        });

        // Make a subscription, and add some data to active registry
        retryableInterestChannel.appendInterest(INTEREST).toBlocking().firstOrDefault(null);
        retryableInterestChannel.appendInterest(INTEREST2).toBlocking().firstOrDefault(null);

        verify(interestChannel1, timeout(1)).appendInterest(INTEREST);
        verify(interestChannel1, timeout(1)).appendInterest(INTEREST2);

        registry.register(INFO, interestChannel1.getSource()).subscribe();
        scheduler.triggerActions();
        assertThat(retryableInterestChannel.associatedRegistry(), instanceOf(PreservableEurekaRegistry.class));
        assertThat((PreservableEurekaRegistry)retryableInterestChannel.associatedRegistry(), equalTo(registry));
        assertThat(registry.size(), is(equalTo(1)));

        registry.register(INFO2, interestChannel1.getSource()).subscribe();
        scheduler.triggerActions();
        assertThat(registry.size(), is(equalTo(2)));

        assertThat(notifications.size(), equalTo(1));
        assertThat(notifications.get(0).getData(), equalTo(INFO));

        // Channel failure; match any interest for channel2 as there will be immediate automatic resubscription
        channelLifecycle1.onError(new Exception("channel error msg"));  // break the channel

        // Move in time till retry point
        scheduler.advanceTimeBy(RetryableInterestChannel.DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS);

        // Check automatic interest subscription for the last interest set
        verify(interestChannel2).appendInterest(new MultipleInterests<>(INTEREST, INTEREST2));

        // Move til eviction timeout
        scheduler.advanceTimeBy(EVICTION_TIMEOUT_MS * 2 - 1, TimeUnit.MILLISECONDS);

        // check that we are not yet allowed to evict
        assertTrue(registry.isInSelfPreservation());
        assertThat(evictionQueue.size(), equalTo(1));  // 1 stuck in queue due to self preservation

        // Verify that we still provide parts of the old registry content (1/2 is evicted before we went into self preservation)
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));

        // Push new content, which should result in:
        // 1. pending subscriptions termination (shutdown first registry which onCompletes)
        // 2. replacing pre-filled registry with a new one

        // push re-push the content sourced from the second channel to simulate server side new notification
        // this should trigger the self preservation threshold and bring the registry out of self preservation
        registry.register(INFO, interestChannel2.getSource()).subscribe();
        registry.register(INFO2, interestChannel2.getSource()).subscribe();
        scheduler.triggerActions();
        assertThat(registry.size(), is(equalTo(2)));

        // check that we are now allowed to evict
        assertFalse(registry.isInSelfPreservation());

        verify(interestChannel1, times(1)).close();
        assertThat(evictionQueue.size(), equalTo(1));

        // Move til eviction timeout to evict the copy from channel 1 that was still there from self preservation earlier
        scheduler.advanceTimeBy(EVICTION_TIMEOUT_MS * 2 - 1, TimeUnit.MILLISECONDS);
        assertThat(evictionQueue.size(), equalTo(0));

        // now try to do updates with source as both channel1 and channel2. We should see notification for the channel2
        // update but not the channel1 update
        InstanceInfo updatedINFOa = new InstanceInfo.Builder().withInstanceInfo(INFO).withStatus(InstanceInfo.Status.DOWN).build();
        InstanceInfo updatedINFOb = new InstanceInfo.Builder().withInstanceInfo(INFO).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
        registry.update(updatedINFOa, updatedINFOa.diffOlder(INFO), interestChannel1.getSource()).subscribe();
        registry.update(updatedINFOb, updatedINFOb.diffOlder(INFO), interestChannel2.getSource()).subscribe();
        scheduler.triggerActions();

        if (notifications.size() == 2) {
            // the stuck item is the interest app, so we should never see the delete notification
            assertThat(notifications.get(0).getData(), equalTo(INFO));
            assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));
            assertThat(notifications.get(1).getData(), equalTo(updatedINFOb));
            assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Modify));
        } else if (notifications.size() == 4) {
            // the stuck item is not the interested app, so we should receive two extra notifications,
            // 1 delete for when the item is evicted, and 1 add for when it is added back via channel2
            assertThat(notifications.get(0).getData(), equalTo(INFO));
            assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));
            assertThat(notifications.get(1).getData(), equalTo(INFO));
            assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Delete));
            assertThat(notifications.get(2).getData(), equalTo(INFO));
            assertThat(notifications.get(2).getKind(), equalTo(ChangeNotification.Kind.Add));
            assertThat(notifications.get(3).getData(), equalTo(updatedINFOb));
            assertThat(notifications.get(3).getKind(), equalTo(ChangeNotification.Kind.Modify));
        } else {
            fail("Should never reach here");
        }

        assertThat(completed.get(), is(false));  // the interest stream should never complete
        assertThat(errored.get(), is(false));  // the interest stream should not error
    }
}