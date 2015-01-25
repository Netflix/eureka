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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.registry.eviction.EvictionItem;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * {@link com.netflix.eureka2.registry.SourcedEurekaRegistry} implementation that cooperates with eviction queue
 * to control expiry of abruptly disconnected client/replication channels.
 *
 * @author Tomasz Bak
 */
public class PreservableEurekaRegistry implements SourcedEurekaRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(PreservableEurekaRegistry.class);

    private final SourcedEurekaRegistry<InstanceInfo> eurekaRegistry;
    private final EvictionQueue evictionQueue;
    private final EvictionStrategy evictionStrategy;
    private final EurekaRegistryMetrics metrics;
    private final Subscription evictionSubscription;
    private final EvictionSubscriber evictionSubscriber;

    /* Visible for testing */ volatile int expectedRegistrySize;
    /* Visible for testing */ final AtomicBoolean selfPreservation = new AtomicBoolean();

    private final Action1<Boolean> increaseExpectedSize = new Action1<Boolean>() {
        @Override
        public void call(Boolean status) {
            if (status) {
                expectedRegistrySize = Math.max(expectedRegistrySize, eurekaRegistry.size());
                resumeEviction();
            }
        }
    };
    private final Action1<Boolean> decreaseExpectedSize = new Action1<Boolean>() {
        @Override
        public void call(Boolean status) {
            if (status) {
                expectedRegistrySize = Math.max(0, expectedRegistrySize - 1);
                resumeEviction();
            }
        }
    };

    public PreservableEurekaRegistry(
            SourcedEurekaRegistry eurekaRegistry,
            EurekaRegistryConfig registryConfig,
            EurekaRegistryMetricFactory metricFactory) {
        this(
                eurekaRegistry,
                new EvictionQueueImpl(registryConfig, metricFactory),
                new EvictionStrategyProvider(registryConfig).get(),
                metricFactory
        );
    }

    @Inject
    public PreservableEurekaRegistry(@Named("delegate") SourcedEurekaRegistry eurekaRegistry,
                                     EvictionQueue evictionQueue,
                                     EvictionStrategy evictionStrategy,
                                     EurekaRegistryMetricFactory metricFactory) {
        this.eurekaRegistry = eurekaRegistry;
        this.evictionQueue = evictionQueue;
        this.evictionStrategy = evictionStrategy;
        this.metrics = metricFactory.getEurekaServerRegistryMetrics();
        this.evictionSubscriber = new EvictionSubscriber();
        this.evictionSubscription = evictionQueue.pendingEvictions().subscribe(evictionSubscriber);
    }

    @Override
    public Observable<Boolean> register(final InstanceInfo instanceInfo, final Source source) {
        Observable<Boolean> result = eurekaRegistry.register(instanceInfo, source);
        result.subscribe(increaseExpectedSize);
        return result;
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo instanceInfo, Source source) {
        Observable<Boolean> result = eurekaRegistry.unregister(instanceInfo, source);
        result.subscribe(decreaseExpectedSize);
        return result;
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
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest, Source.Matcher sourceMatcher) {
        return eurekaRegistry.forSnapshot(interest, sourceMatcher);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return eurekaRegistry.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.Matcher sourceMatcher) {
        return eurekaRegistry.forInterest(interest, sourceMatcher);
    }

    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        return Observable.error(new UnsupportedOperationException("getHolders is not supported for PreservableEurekaRegistry"));
    }

    public boolean isInSelfPreservation() {
        return selfPreservation.get();
    }

    /**
     * Evict by sending to the evictionQueue instead of directly.
     */
    @Override
    public Observable<Long> evictAll(final Source source) {
        return eurekaRegistry.getHolders()
                .map(new Func1<MultiSourcedDataHolder<InstanceInfo>, Void>() {
                    @Override
                    public Void call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        evictionQueue.add(holder.get(source), source);
                        return null;
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.error("Error evicting registry for source {}", source, throwable);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        logger.info("Completed evicting registry for source {}", source);
                    }
                })
                .countLong();

    }

    /**
     * Evict by sending to the evictionQueue instead of directly.
     */
    @Override
    public Observable<Long> evictAll() {
        return eurekaRegistry.getHolders()
                .map(new Func1<MultiSourcedDataHolder<InstanceInfo>, Void>() {
                    @Override
                    public Void call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        for (Source source : holder.getAllSources()) {
                            evictionQueue.add(holder.get(source), source);
                        }
                        return null;
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.error("Error evicting registry", throwable);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        logger.info("Completed evicting registry");
                    }
                })
                .countLong();

    }

    @Override
    public Observable<Void> shutdown() {
        evictionSubscription.unsubscribe();
        evictionQueue.shutdown();
        return eurekaRegistry.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        evictionSubscription.unsubscribe();
        evictionQueue.shutdown();
        return eurekaRegistry.shutdown(cause);
    }

    private boolean allowedToEvict() {
        return evictionStrategy.allowedToEvict(expectedRegistrySize, eurekaRegistry.size()) > 0;
    }

    private void resumeEviction() {
        if (selfPreservation.compareAndSet(true, false)) {
            metrics.setSelfPreservation(false);
            logger.info("Coming out of self preservation mode");
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
            if (allowedToEvict()) {
                eurekaRegistry.unregister(evictionItem.getInstanceInfo(), evictionItem.getSource())
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                logger.info("Successfully evicted registry entry {}/{}",
                                        evictionItem.getSource(), evictionItem.getInstanceInfo().getId());
                                resume();
                            }
                        })
                        .retry(2)
                        .subscribe();
            } else {
                // TODO We should not do that, as this resets the timeout for this entry. Once item was provided, we should remove it (no need to be too strict).
                evictionQueue.add(evictionItem.getInstanceInfo(), evictionItem.getSource());  // add back to the eviction queue
                logger.info("Not evicting registry entry, adding back to the queue {}/{}",
                        evictionItem.getSource(), evictionItem.getInstanceInfo().getId());
                selfPreservation.set(true);
                metrics.setSelfPreservation(true);
                logger.info("Entering self preservation mode");
            }
        }

        public void resume() {
            request(1);
        }
    }
}
