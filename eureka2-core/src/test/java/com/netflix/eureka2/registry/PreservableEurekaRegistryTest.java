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

package com.netflix.eureka2.registry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.registry.eviction.EvictionItem;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class PreservableEurekaRegistryTest {

    private final EurekaRegistryMetricFactory registryMetricFactory = mock(EurekaRegistryMetricFactory.class);
    private final EurekaRegistryMetrics registryMetrics = mock(EurekaRegistryMetrics.class);
    private final SerializedTaskInvokerMetrics registryTaskInvokerMetrics = mock(SerializedTaskInvokerMetrics.class);

    private final SourcedEurekaRegistry<InstanceInfo> baseRegistry = mock(SourcedEurekaRegistry.class);

    private static final InstanceInfo DISCOVERY = SampleInstanceInfo.DiscoveryServer.build();
    private final Source localSource = new Source(Source.Origin.LOCAL);
    private final Source remoteSource = new Source(Source.Origin.REPLICATED, "test");

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
        when(registryMetricFactory.getEurekaServerRegistryMetrics()).thenReturn(registryMetrics);
        when(registryMetricFactory.getRegistryTaskInvokerMetrics()).thenReturn(registryTaskInvokerMetrics);

        when(evictionQueue.pendingEvictions()).thenReturn(evictionItemObservable);
        preservableRegistry = new PreservableEurekaRegistry(baseRegistry, evictionQueue, evictionStrategy, registryMetricFactory);
    }

    @After
    public void tearDown() throws Exception {
        preservableRegistry.shutdown();
    }

    @Test(timeout = 60000)
    public void testRemovesEvictedItemsWhenNotInSelfPreservationMode() throws Exception {
        when(baseRegistry.register(DISCOVERY, localSource)).thenReturn(Observable.just(true));
        preservableRegistry.register(DISCOVERY, localSource);

        when(baseRegistry.unregister(DISCOVERY, remoteSource)).thenReturn(Observable.just(true));
        // Now evict item, and check that PreservableEurekaRegistry asks for more
        evict(DISCOVERY, remoteSource, 1);
        verify(baseRegistry, times(1)).unregister(DISCOVERY, remoteSource);

        assertThat(requestedEvictedItems.get(), is(equalTo(1L)));
    }

    @Test(timeout = 60000)
    public void testDoesNotRemoveEvictedItemsWhenInSelfPreservationMode() throws Exception {
        when(baseRegistry.register(DISCOVERY, localSource)).thenReturn(Observable.just(true));
        preservableRegistry.register(DISCOVERY, localSource);

        when(baseRegistry.unregister(DISCOVERY, remoteSource)).thenReturn(Observable.just(true));
        // Now evict item, and check that nothing is requested
        evict(DISCOVERY, remoteSource, 0);
        verify(baseRegistry, times(0)).unregister(DISCOVERY, remoteSource);

        assertThat(requestedEvictedItems.get(), is(equalTo(0L)));

        // Now add one more item, turn off self-preservation, and check that eviction consumption is resumed
        when(evictionStrategy.allowedToEvict(anyInt(), anyInt())).thenReturn(1);
        preservableRegistry.register(DISCOVERY, localSource);

        assertThat(requestedEvictedItems.get(), is(equalTo(1L)));
    }

    @Test(timeout = 60000)
    public void testBehavesAsRegularRegistryForNonEvictedItems() throws Exception {
        // Register
        when(baseRegistry.register(DISCOVERY, localSource)).thenReturn(Observable.just(true));
        when(baseRegistry.size()).thenReturn(1);
        Observable<Boolean> status = preservableRegistry.register(DISCOVERY, localSource);
        assertStatus(status, true);

        assertThat(preservableRegistry.size(), is(equalTo(1)));
        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.register(DISCOVERY, remoteSource)).thenReturn(Observable.just(false));
        status = preservableRegistry.register(DISCOVERY, remoteSource);
        assertStatus(status, false);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        // Update
        InstanceInfo update = new InstanceInfo.Builder().withInstanceInfo(DISCOVERY).withVipAddress("aNewName").build();
        when(baseRegistry.register(update, localSource)).thenReturn(Observable.just(false));
        status = preservableRegistry.register(update, localSource);
        assertStatus(status, false);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.register(update, remoteSource)).thenReturn(Observable.just(false));
        status = preservableRegistry.register(DISCOVERY, remoteSource);
        assertStatus(status, false);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        // Unregister
        when(baseRegistry.unregister(DISCOVERY, localSource)).thenReturn(Observable.just(false));
        status = preservableRegistry.unregister(DISCOVERY, localSource);
        assertStatus(status, false);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(1)));

        when(baseRegistry.unregister(DISCOVERY, remoteSource)).thenReturn(Observable.just(true));
        when(baseRegistry.size()).thenReturn(0);
        status = preservableRegistry.unregister(DISCOVERY, remoteSource);
        assertStatus(status, true);

        assertThat(preservableRegistry.expectedRegistrySize, is(equalTo(0)));
    }

    @Test(timeout = 60000)
    public void testDelegatesInterestSubscriptions() throws Exception {
        Observable<ChangeNotification<InstanceInfo>> notification1 = Observable.just(new ChangeNotification<>(Kind.Add, DISCOVERY));
        when(baseRegistry.forInterest(Interests.forFullRegistry())).thenReturn(notification1);
        assertSame(notification1, preservableRegistry.forInterest(Interests.forFullRegistry()));

        Source.Matcher matcher = Source.matcherFor(remoteSource);
        Observable<ChangeNotification<InstanceInfo>> notification2 = Observable.just(new ChangeNotification<>(Kind.Add, DISCOVERY));
        when(baseRegistry.forInterest(Interests.forFullRegistry(), matcher)).thenReturn(notification2);
        assertSame(notification2, preservableRegistry.forInterest(Interests.forFullRegistry(), matcher));

        Observable<InstanceInfo> notification3 = Observable.just(DISCOVERY);
        when(baseRegistry.forSnapshot(Interests.forFullRegistry())).thenReturn(notification3);
        assertSame(notification3, preservableRegistry.forSnapshot(Interests.forFullRegistry()));
    }

    @Test(timeout = 60000)
    public void testCleansUpResources() throws Exception {
        preservableRegistry.shutdown();

        assertFalse(evictionPublisher.hasObservers());
        verify(baseRegistry, times(1)).shutdown();
    }

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        // Add one item to the registry
        when(baseRegistry.register(DISCOVERY, localSource)).thenReturn(Observable.just(true));
        preservableRegistry.register(DISCOVERY, localSource);

        // Try to evict the item, forcing entering self preservation mode
        evict(DISCOVERY, remoteSource, 0);
        verify(registryMetrics, times(1)).setSelfPreservation(true);

        // Now add one more item to the registry, and simulate leaving self preservation mode
        InstanceInfo secondEntry = SampleInstanceInfo.EurekaWriteServer.build();
        when(baseRegistry.unregister(DISCOVERY, remoteSource)).thenReturn(Observable.just(true));
        when(baseRegistry.register(secondEntry, localSource)).thenReturn(Observable.just(true));

        preservableRegistry.register(secondEntry, localSource);

        verify(registryMetrics, times(1)).setSelfPreservation(false);
    }

    private void evict(InstanceInfo instanceInfo, Source source, int allowedToEvict) {
        when(evictionStrategy.allowedToEvict(anyInt(), anyInt())).thenReturn(allowedToEvict);
        evictionPublisher.onNext(new EvictionItem(instanceInfo, source, 1));
        requestedEvictedItems.decrementAndGet();
    }

    private static void assertStatus(Observable<Boolean> actual, Boolean expected) {
        Boolean value = actual.timeout(1, TimeUnit.SECONDS).toBlocking().firstOrDefault(null);
        assertThat(value, is(equalTo(expected)));
    }
}