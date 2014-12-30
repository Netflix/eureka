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

package com.netflix.eureka2.server.registry;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistry.Status;
import com.netflix.eureka2.server.registry.eviction.EvictionItem;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.server.metric.WriteServerMetricFactory.writeServerMetrics;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class PreservableEurekaRegistryTest {

    private final EurekaServerRegistry<InstanceInfo> baseRegistry = mock(EurekaServerRegistry.class);

    private static final InstanceInfo DISCOVERY = SampleInstanceInfo.DiscoveryServer.build();
    private static final Set<Delta<?>> DISCOVERY_DELTAS = new HashSet<>();
    private static final Source REMOTE_SOURCE = Source.replicationSource("test");

    private final EvictionQueue evictionQueue = mock(EvictionQueue.class);
    private final EvictionStrategy evictionStrategy = mock(EvictionStrategy.class);

    private final AtomicLong requestedEvictedItems = new AtomicLong();
    private final PublishSubject<EvictionItem> evictionPublisher = PublishSubject.create();
    private final Observable<EvictionItem> evictionItemObservable = Observable.create(new OnSubscribe<EvictionItem>() {
        @Override
        public void call(Subscriber<? super EvictionItem> subscriber) {
            subscriber.setProducer(new Producer() {
                @Override
                public void request(long n) {
                    requestedEvictedItems.addAndGet(n);
                }
            });
            evictionPublisher.subscribe(subscriber);
        }
    });

    private PreservableEurekaRegistry preservableRegistry;

    @Before
    public void setUp() throws Exception {
        when(evictionQueue.pendingEvictions()).thenReturn(evictionItemObservable);
        preservableRegistry = new PreservableEurekaRegistry(baseRegistry, evictionQueue, evictionStrategy, writeServerMetrics());
    }

    @Test
    public void testRemovesEvictedItemsWhenNotInSelfPreservationMode() throws Exception {
        when(baseRegistry.register(DISCOVERY)).thenReturn(Observable.just(Status.AddedFirst));
        preservableRegistry.register(DISCOVERY);

        // Now evict item, and check that PreservableEurekaRegistry asks for more
        evict(DISCOVERY, REMOTE_SOURCE, 1);
        verify(baseRegistry, times(1)).unregister(DISCOVERY, REMOTE_SOURCE);

        assertThat(requestedEvictedItems.get(), is(equalTo(1L)));
    }

    @Test
    public void testDoesNotRemoveEvictedItemsWhenInSelfPreservationMode() throws Exception {
        when(baseRegistry.register(DISCOVERY)).thenReturn(Observable.just(Status.AddedFirst));
        preservableRegistry.register(DISCOVERY);

        // Now evict item, and check that nothing is requested
        evict(DISCOVERY, REMOTE_SOURCE, 0);
        verify(baseRegistry, times(1)).unregister(DISCOVERY, REMOTE_SOURCE);

        assertThat(requestedEvictedItems.get(), is(equalTo(0L)));

        // Now add one more item, turn off self-preservation, and check that eviction consumption is resumed
        when(evictionStrategy.allowedToEvict(anyInt(), anyInt())).thenReturn(1);
        preservableRegistry.register(DISCOVERY);

        assertThat(requestedEvictedItems.get(), is(equalTo(1L)));
    }

    private void evict(InstanceInfo instanceInfo, Source source, int allowedToEvict) {
        when(evictionStrategy.allowedToEvict(anyInt(), anyInt())).thenReturn(allowedToEvict);
        evictionPublisher.onNext(new EvictionItem(instanceInfo, source, 1));
        requestedEvictedItems.decrementAndGet();
    }

    @Test
    public void testBehavesAsRegularRegistryForNonEvictedItems() throws Exception {
        // Register
        when(baseRegistry.register(DISCOVERY)).thenReturn(Observable.just(Status.AddedFirst));
        when(baseRegistry.size()).thenReturn(1);
        Observable<Status> status = preservableRegistry.register(DISCOVERY);
        assertStatus(status, Status.AddedFirst);

        assertThat(preservableRegistry.size(), is(equalTo(1)));
        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.register(DISCOVERY, REMOTE_SOURCE)).thenReturn(Observable.just(Status.AddedChange));
        status = preservableRegistry.register(DISCOVERY, REMOTE_SOURCE);
        assertStatus(status, Status.AddedChange);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        // Update
        when(baseRegistry.update(DISCOVERY, DISCOVERY_DELTAS)).thenReturn(Observable.just(Status.AddedChange));
        status = preservableRegistry.update(DISCOVERY, DISCOVERY_DELTAS);
        assertStatus(status, Status.AddedChange);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.update(DISCOVERY, DISCOVERY_DELTAS, REMOTE_SOURCE)).thenReturn(Observable.just(Status.AddedChange));
        status = preservableRegistry.update(DISCOVERY, DISCOVERY_DELTAS, REMOTE_SOURCE);
        assertStatus(status, Status.AddedChange);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        // Unregister
        when(baseRegistry.unregister(DISCOVERY)).thenReturn(Observable.just(Status.RemovedFragment));
        status = preservableRegistry.unregister(DISCOVERY);
        assertStatus(status, Status.RemovedFragment);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.unregister(DISCOVERY, REMOTE_SOURCE)).thenReturn(Observable.just(Status.RemovedLast));
        when(baseRegistry.size()).thenReturn(0);
        status = preservableRegistry.unregister(DISCOVERY, REMOTE_SOURCE);
        assertStatus(status, Status.RemovedLast);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(0)));
    }

    @Test
    public void testDelegatesInterestSubscriptions() throws Exception {
        Observable<ChangeNotification<InstanceInfo>> notification1 = Observable.just(new ChangeNotification<InstanceInfo>(Kind.Add, DISCOVERY));
        when(baseRegistry.forInterest(Interests.forFullRegistry())).thenReturn(notification1);
        assertSame(preservableRegistry.forInterest(Interests.forFullRegistry()), notification1);

        Observable<ChangeNotification<InstanceInfo>> notification2 = Observable.just(new ChangeNotification<InstanceInfo>(Kind.Add, DISCOVERY));
        when(baseRegistry.forInterest(Interests.forFullRegistry(), REMOTE_SOURCE)).thenReturn(notification2);
        assertSame(preservableRegistry.forInterest(Interests.forFullRegistry(), REMOTE_SOURCE), notification2);

        Observable<InstanceInfo> notification3 = Observable.just(DISCOVERY);
        when(baseRegistry.forSnapshot(Interests.forFullRegistry())).thenReturn(notification3);
        assertSame(preservableRegistry.forSnapshot(Interests.forFullRegistry()), notification3);
    }

    @Test
    public void testCleansUpResources() throws Exception {
        preservableRegistry.shutdown();

        assertFalse(evictionPublisher.hasObservers());
        verify(baseRegistry, times(1)).shutdown();
    }

    private static void assertStatus(Observable<Status> actual, Status expected) {
        Status value = actual.timeout(1, TimeUnit.SECONDS).toBlocking().firstOrDefault(null);
        assertThat(value, is(equalTo(expected)));
    }
}