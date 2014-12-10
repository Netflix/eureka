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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.NotificationsSubject;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.metric.EurekaServerRegistryMetrics;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.NotifyingInstanceInfoHolder.NotificationTaskInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

/**
 * @author David Liu
 */
public class EurekaServerRegistryImpl implements EurekaServerRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerRegistryImpl.class);

    /**
     * TODO: define a better contract for base implementation and decorators
     */
    protected final ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore;
    private final MultiSourcedDataHolder.HolderStoreAccessor<NotifyingInstanceInfoHolder> internalStoreAccessor;
    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaServerRegistryMetrics metrics;
    private final NotificationTaskInvoker invoker;

    @Inject
    public EurekaServerRegistryImpl(WriteServerMetricFactory metricsFactory) {
        this(metricsFactory, Schedulers.computation());
    }

    public EurekaServerRegistryImpl(WriteServerMetricFactory metricsFactory, Scheduler scheduler) {
        this.metrics = metricsFactory.getEurekaServerRegistryMetrics();
        this.metrics.setRegistrySizeMonitor(this);

        invoker = new NotificationTaskInvoker(
                metricsFactory.getRegistryTaskInvokerMetrics(),
                scheduler);

        internalStore = new ConcurrentHashMap<>();
        indexRegistry = new IndexRegistryImpl<>();
        notificationSubject = NotificationsSubject.create();

        internalStoreAccessor = new MultiSourcedDataHolder.HolderStoreAccessor<NotifyingInstanceInfoHolder>() {
            @Override
            public void add(NotifyingInstanceInfoHolder holder) {
                internalStore.put(holder.getId(), holder);
            }

            @Override
            public NotifyingInstanceInfoHolder get(String id) {
                return internalStore.get(id);
            }

            @Override
            public void remove(String id) {
                internalStore.remove(id);
            }

            @Override
            public boolean contains(String id) {
                return internalStore.containsKey(id);
            }
        };
    }

    // -------------------------------------------------
    // Registry manipulation
    // -------------------------------------------------

    @Override
    public Observable<Status> register(final InstanceInfo instanceInfo) {
        return register(instanceInfo, Source.localSource());
    }

    @Override
    public Observable<Status> register(final InstanceInfo instanceInfo, final Source source) {
        MultiSourcedDataHolder<InstanceInfo> holder = new NotifyingInstanceInfoHolder(
                internalStoreAccessor, notificationSubject, invoker, instanceInfo.getId());

        Observable<Status> result = holder.update(source, instanceInfo).doOnNext(new Action1<Status>() {
            @Override
            public void call(Status status) {
                if (status != Status.AddExpired) {
                    metrics.incrementRegistrationCounter(source.getOrigin());
                }
            }
        });
        return subscribeToUpdateResult(result);
    }

    @Override
    public Observable<Status> unregister(final InstanceInfo instanceInfo) {
        return unregister(instanceInfo, Source.localSource());
    }

    @Override
    public Observable<Status> unregister(final InstanceInfo instanceInfo, final Source source) {
        final MultiSourcedDataHolder<InstanceInfo> currentHolder = internalStore.get(instanceInfo.getId());
        if (currentHolder == null) {
            return Observable.just(Status.RemoveExpired);
        }

        Observable<Status> result = currentHolder.remove(source, instanceInfo).doOnNext(new Action1<Status>() {
            @Override
            public void call(Status status) {
                if (status != Status.RemoveExpired) {
                    metrics.incrementUnregistrationCounter(source.getOrigin());
                }
            }
        });
        return subscribeToUpdateResult(result);
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return update(updatedInfo, deltas, Source.localSource());
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas, final Source source) {
        MultiSourcedDataHolder<InstanceInfo> holder = new NotifyingInstanceInfoHolder(
                internalStoreAccessor, notificationSubject, invoker, updatedInfo.getId());

        Observable<Status> result = holder.update(source, updatedInfo).doOnNext(new Action1<Status>() {
            @Override
            public void call(Status status) {
                if (status != Status.AddExpired) {
                    metrics.incrementUpdateCounter(source.getOrigin());
                }
            }
        });
        return subscribeToUpdateResult(result);
    }

    /**
     * TODO: do we have to eagerly subscribe? This code is inefficient.
     */
    private static Observable<Status> subscribeToUpdateResult(Observable<Status> status) {
        final ReplaySubject<Status> result = ReplaySubject.create();
        status.subscribe(new Subscriber<Status>() {
            @Override
            public void onCompleted() {
                result.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Registry update failure", e);
                result.onError(e);
            }

            @Override
            public void onNext(Status status) {
                logger.debug("Registry updated completed with status {}", status);
                result.onNext(status);
            }
        });
        return result;
    }

    @Override
    public int size() {
        return internalStore.size();
    }

    /**
     * Return a snapshot of the current registry for the passed {@code interest} as a stream of {@link InstanceInfo}s.
     * This view of the snapshot is eventual consistent and any instances that successfully registers while the
     * stream is being processed might be added to the stream. Note that this stream terminates as opposed to
     * forInterest.
     *
     * @return A stream of {@link InstanceInfo}s for the passed {@code interest}. The stream represent a snapshot
     * of the registry for the interest.
     */
    @Override
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest) {
        return Observable.from(internalStore.values())
                .map(new Func1<MultiSourcedDataHolder<InstanceInfo>, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        ChangeNotification<InstanceInfo> notification = holder.getChangeNotification();
                        return notification == null ? null : notification.getData();
                    }
                })
                .filter(new Func1<InstanceInfo, Boolean>() {
                    @Override
                    public Boolean call(InstanceInfo instanceInfo) {
                        return instanceInfo != null && interest.matches(instanceInfo);
                    }
                });
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest, final Source source) {
        return forSnapshot(interest).filter(new Func1<InstanceInfo, Boolean>() {
            @Override
            public Boolean call(InstanceInfo instanceInfo) {
                MultiSourcedDataHolder<InstanceInfo> holder = internalStore.get(instanceInfo.getId());
                return holder != null && source.equals(holder.getSource());
            }
        });
    }

    /**
     * Return an observable of all matching InstanceInfo for the current registry snapshot,
     * as {@link ChangeNotification}s
     * @param interest
     * @return an observable of all matching InstanceInfo for the current registry snapshot,
     * as {@link ChangeNotification}s
     */
    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        try {
            // TODO: this method can be run concurrently from different channels, unless we run everything on single server event loop.
            // It is possible that the same instance info will be both in snapshot and paused notification queue.
            notificationSubject.pause(); // Pause notifications till we get a snapshot of current registry (registry.values())
            if (interest instanceof MultipleInterests) {
                return indexRegistry.forCompositeInterest((MultipleInterests) interest, this);
            } else {
                return indexRegistry.forInterest(interest, notificationSubject,
                        new InstanceInfoInitStateHolder(getSnapshotForInterest(interest)));
            }
        } finally {
            notificationSubject.resume();
        }
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Source source) {
        return forInterest(interest).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                if (changeNotification instanceof Sourced) {
                    Source notificationSource = ((Sourced) changeNotification).getSource();
                    return source.equals(notificationSource);
                } else {
                    logger.warn("Received notification without a source, {}", changeNotification);
                    return false;
                }
            }
        });
    }

    @Override
    public Observable<Void> shutdown() {
        return indexRegistry.shutdown();
    }

    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        final Collection<NotifyingInstanceInfoHolder> eurekaHolders = internalStore.values();
        return new FilteredIterator(interest, eurekaHolders.iterator());
    }

    private static class FilteredIterator implements Iterator<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final Iterator<NotifyingInstanceInfoHolder> delegate;
        private ChangeNotification<InstanceInfo> next;

        private FilteredIterator(Interest<InstanceInfo> interest, Iterator<NotifyingInstanceInfoHolder> delegate) {
            this.interest = interest;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            if (null != next) {
                return true;
            }

            while (delegate.hasNext()) { // Iterate till we get a matching item.
                MultiSourcedDataHolder<InstanceInfo> possibleNext = delegate.next();
                ChangeNotification<InstanceInfo> notification = possibleNext.getChangeNotification();
                if (notification != null && interest.matches(notification.getData())) {
                    next = notification;
                    return true;
                }
            }
            return false;
        }

        @Override
        public ChangeNotification<InstanceInfo> next() {
            if (hasNext()) {
                ChangeNotification<InstanceInfo> next = this.next;
                this.next = null; // Forces hasNext() to peek the next item.
                return next;
            }
            throw new NoSuchElementException("No more notifications.");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported for this iterator.");
        }
    }

    // pretty print for debugging
    @Override
    public String toString() {
        return prettyString();
    }

    private String prettyString() {
        StringBuilder sb = new StringBuilder("EurekaRegistryImpl\n");
        for (Map.Entry<String, NotifyingInstanceInfoHolder> entry : internalStore.entrySet()) {
            sb.append(entry).append("\n");
        }
        sb.append(indexRegistry.toString());

        return sb.toString();
    }
}
