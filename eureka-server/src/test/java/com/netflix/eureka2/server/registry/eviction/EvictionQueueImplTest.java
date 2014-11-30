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

package com.netflix.eureka2.server.registry.eviction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.registry.Source;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Notification.Kind;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.server.metric.EurekaServerMetricFactory.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class EvictionQueueImplTest {

    private static final long EVICTION_TIMEOUT = 1000;

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaServerConfig config = EurekaServerConfig.baseBuilder().withEvictionTimeout(EVICTION_TIMEOUT).build();

    private final EvictionQueueImpl evictionQueue = new EvictionQueueImpl(config, serverMetrics(), testScheduler);
    private final List<EvictionItem> evictedList = new ArrayList<>();
    private final EvictionQueueSubscriber evictionQueueSubscriber = new EvictionQueueSubscriber();

    @Before
    public void setUp() throws Exception {
        evictionQueue.pendingEvictions().subscribe(evictionQueueSubscriber);
    }

    @Test
    public void testAddedItemIsEvictedWhenExpires() throws Exception {
        evictionQueueSubscriber.allow(1);

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        evictionQueue.add(instanceInfo, Source.localSource());

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
        evictionQueue.add(instanceInfo, Source.localSource());

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