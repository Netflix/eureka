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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.eviction.EvictionItem;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategy;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;

/**
 * {@link EurekaServerRegistry} implementation that cooperates with eviction queue
 * to control expiry of abruptly disconnected client/replication channels.
 *
 * @author Tomasz Bak
 */
public class PreservableEurekaRegistry implements EurekaServerRegistry<InstanceInfo> {

    private final EurekaServerRegistry<InstanceInfo> eurekaRegistry;
    private final EvictionStrategy evictionStrategy;
    private final Subscription evictionSubscription;
    private final EvictionSubscriber evictionSubscriber;

    /* Visible for testing */ volatile int expectedRegistrySize;
    /* Visible for testing */ final AtomicBoolean selfPreservation = new AtomicBoolean();

    private final Action1<Status> increaseExpectedSize = new Action1<Status>() {
        @Override
        public void call(Status status) {
            if (status == Status.AddedFirst) {
                expectedRegistrySize = Math.max(expectedRegistrySize, eurekaRegistry.size());
                resumeEviction();
            }
        }
    };
    private final Action1<Status> decreaseExpectedSize = new Action1<Status>() {
        @Override
        public void call(Status status) {
            if (status == Status.RemovedLast) {
                expectedRegistrySize = Math.max(0, expectedRegistrySize - 1);
                resumeEviction();
            }
        }
    };

    @Inject
    public PreservableEurekaRegistry(@Named("delegate") EurekaServerRegistry eurekaRegistry,
                                     EvictionQueue evictionQueue,
                                     EvictionStrategy evictionStrategy,
                                     EurekaServerMetricFactory metricFactory) {
        this.eurekaRegistry = eurekaRegistry;
        this.evictionStrategy = evictionStrategy;
        this.evictionSubscriber = new EvictionSubscriber();
        this.evictionSubscription = evictionQueue.pendingEvictions().subscribe(evictionSubscriber);

        metricFactory.getEurekaServerRegistryMetrics().setSelfPreservationMonitor(this);
    }

    @Override
    public Observable<Status> register(final InstanceInfo instanceInfo) {
        Observable<Status> result = eurekaRegistry.register(instanceInfo);
        result.subscribe(increaseExpectedSize);
        return result;
    }

    @Override
    public Observable<Status> register(final InstanceInfo instanceInfo, final Source source) {
        Observable<Status> result = eurekaRegistry.register(instanceInfo, source);
        result.subscribe(increaseExpectedSize);
        return result;
    }

    @Override
    public Observable<Status> unregister(InstanceInfo instanceInfo) {
        Observable<Status> result = eurekaRegistry.unregister(instanceInfo);
        result.subscribe(decreaseExpectedSize);
        return result;
    }

    @Override
    public Observable<Status> unregister(InstanceInfo instanceInfo, Source source) {
        Observable<Status> result = eurekaRegistry.unregister(instanceInfo, source);
        result.subscribe(decreaseExpectedSize);
        return result;
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return eurekaRegistry.update(updatedInfo, deltas);
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas, Source source) {
        return eurekaRegistry.update(updatedInfo, deltas, source);
    }

    @Override
    public int size() {
        return eurekaRegistry.size();
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return eurekaRegistry.forSnapshot(interest);
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest, Source source) {
        return eurekaRegistry.forSnapshot(interest, source);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return eurekaRegistry.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source source) {
        return eurekaRegistry.forInterest(interest, source);
    }

    public boolean isInSelfPreservation() {
        return selfPreservation.get();
    }

    @Override
    public Observable<Void> shutdown() {
        evictionSubscription.unsubscribe();
        return eurekaRegistry.shutdown();
    }

    private boolean allowedToEvict() {
        return evictionStrategy.allowedToEvict(expectedRegistrySize, eurekaRegistry.size()) > 0;
    }

    private void resumeEviction() {
        if (allowedToEvict() && selfPreservation.compareAndSet(true, false)) {
            evictionSubscriber.resume();
        }
    }

    private class EvictionSubscriber extends Subscriber<EvictionItem> {

        @Override
        public void onStart() {
            request(1);
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
        }

        @Override
        public void onNext(final EvictionItem evictionItem) {
            eurekaRegistry.unregister(evictionItem.getInstanceInfo(), evictionItem.getSource());
            if (allowedToEvict()) {
                resume();
            } else {
                selfPreservation.set(true);
            }
        }

        public void resume() {
            request(1);
        }
    }
}
