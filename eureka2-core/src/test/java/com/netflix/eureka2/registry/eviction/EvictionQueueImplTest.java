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

package com.netflix.eureka2.registry.eviction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EvictionQueueMetrics;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Notification.Kind;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EvictionQueueImplTest {

    private static final long EVICTION_TIMEOUT = 1000;

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaRegistryMetricFactory registryMetricFactory = mock(EurekaRegistryMetricFactory.class);
    private final EvictionQueueMetrics evictionQueueMetrics = mock(EvictionQueueMetrics.class);

    private EvictionQueueImpl evictionQueue;
    private final List<EvictionItem> evictedList = new ArrayList<>();
    private final EvictionQueueSubscriber evictionQueueSubscriber = new EvictionQueueSubscriber();
    private final Source localSource = new Source(Source.Origin.LOCAL);

    @Before
    public void setUp() throws Exception {
        when(registryMetricFactory.getEvictionQueueMetrics()).thenReturn(evictionQueueMetrics);

        evictionQueue = new EvictionQueueImpl(EVICTION_TIMEOUT, registryMetricFactory, testScheduler);
        evictionQueue.pendingEvictions().subscribe(evictionQueueSubscriber);
    }

    @Test
    public void testAddedItemIsEvictedWhenExpires() throws Exception {
        evictionQueueSubscriber.allow(1);

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        evictionQueue.add(instanceInfo, localSource);

        testScheduler.advanceTimeBy(EVICTION_TIMEOUT - 1, TimeUnit.MILLISECONDS);
        assertThat(evictedList.size(), is(equalTo(0)));

        // Advance time pass the item expiry time
        testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

        assertThat(evictedList.size(), is(equalTo(1)));
        assertThat(evictedList.get(0).getInstanceInfo(), is(equalTo(instanceInfo)));
    }

    @Test
    public void testItemsAreNotEvictedIfNotAllowed() throws Exception {
        evictionQueueSubscriber.allow(0);

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        evictionQueue.add(instanceInfo, localSource);

        // Advance time pass the item expiry time
        testScheduler.advanceTimeBy(EVICTION_TIMEOUT, TimeUnit.MILLISECONDS);

        assertThat(evictedList.size(), is(equalTo(0)));

        // Now increase the quota
        evictionQueueSubscriber.allow(1);
        testScheduler.advanceTimeBy(EVICTION_TIMEOUT, TimeUnit.MILLISECONDS);

        assertThat(evictedList.size(), is(equalTo(1)));
        assertThat(evictedList.get(0).getInstanceInfo(), is(equalTo(instanceInfo)));
    }

    @Test
    public void testOnlyOneEvictedQueueSubscriptionAllowed() throws Exception {
        Notification<EvictionItem> notification = evictionQueue
                .pendingEvictions()
                .timeout(1, TimeUnit.SECONDS)
                .materialize()
                .toBlocking().last();
        assertThat(notification.getKind(), is(Kind.OnError));
        assertThat(notification.getThrowable(), instanceOf(IllegalStateException.class));
    }

    @Test
    public void testMetrics() throws Exception {
        // Add an item to the eviction queue
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        evictionQueue.add(instanceInfo, localSource);

        verify(evictionQueueMetrics, times(1)).incrementEvictionQueueAddCounter();
        verify(evictionQueueMetrics, times(1)).setEvictionQueueSize(1);

        // Consume item from the eviction queue
        evictionQueueSubscriber.allow(1);
        testScheduler.advanceTimeBy(EVICTION_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(evictionQueueMetrics, times(1)).decrementEvictionQueueCounter();
        verify(evictionQueueMetrics, times(1)).setEvictionQueueSize(0);
    }

    class EvictionQueueSubscriber extends Subscriber<EvictionItem> {

        void allow(int n) {
            request(n);
        }

        @Override
        public void onStart() {
            request(0);
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
        }

        @Override
        public void onNext(EvictionItem evictionItem) {
            evictedList.add(evictionItem);
        }
    }
}