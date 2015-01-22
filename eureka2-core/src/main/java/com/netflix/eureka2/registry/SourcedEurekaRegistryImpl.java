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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.NotificationsSubject;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;

/**
 * An implementation of {@link com.netflix.eureka2.registry.SourcedEurekaRegistry} that uses a
 * {@link com.netflix.eureka2.registry.MultiSourcedDataHolder} to store multiple copies of entries when the copies
 * are from independent sources. Note that a true register/update/unregister result is only returned when the
 * entries holder is added/removed.
 *
 * @author David Liu
 */
public class SourcedEurekaRegistryImpl implements SourcedEurekaRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(SourcedEurekaRegistryImpl.class);

    /**
     * TODO: define a better contract for base implementation and decorators
     */
    protected final ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore;
    private final MultiSourcedDataHolder.HolderStoreAccessor<NotifyingInstanceInfoHolder> internalStoreAccessor;
    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaRegistryMetrics metrics;
    private final NotifyingInstanceInfoHolder.NotificationTaskInvoker invoker;

    @Inject
    public SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory metricsFactory) {
        this(metricsFactory, Schedulers.computation());
    }

    public SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory metricsFactory, Scheduler scheduler) {
        this.metrics = metricsFactory.getEurekaServerRegistryMetrics();
        this.metrics.setRegistrySizeMonitor(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return size();
            }
        });

        invoker = new NotifyingInstanceInfoHolder.NotificationTaskInvoker(
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
    public Observable<Boolean> register(final InstanceInfo instanceInfo, final Source source) {
        MultiSourcedDataHolder<InstanceInfo> holder = new NotifyingInstanceInfoHolder(
                internalStoreAccessor, notificationSubject, invoker, instanceInfo.getId());

        Observable<MultiSourcedDataHolder.Status> result = holder.update(source, instanceInfo).doOnNext(new Action1<MultiSourcedDataHolder.Status>() {
            @Override
            public void call(MultiSourcedDataHolder.Status status) {
                if (status != MultiSourcedDataHolder.Status.AddExpired) {
                    metrics.incrementRegistrationCounter(source.getOrigin().name());
                }
            }
        });
        return subscribeToUpdateResult(result);
    }

    @Override
    public Observable<Boolean> unregister(final InstanceInfo instanceInfo, final Source source) {
        final MultiSourcedDataHolder<InstanceInfo> currentHolder = internalStore.get(instanceInfo.getId());
        if (currentHolder == null) {
            return Observable.just(false);
        }

        Observable<MultiSourcedDataHolder.Status> result = currentHolder.remove(source).doOnNext(new Action1<MultiSourcedDataHolder.Status>() {
            @Override
            public void call(MultiSourcedDataHolder.Status status) {
                if (status != MultiSourcedDataHolder.Status.RemoveExpired) {
                    metrics.incrementUnregistrationCounter(source.getOrigin().name());
                }
            }
        });
        return subscribeToUpdateResult(result);
    }

    /**
     * TODO: do we have to eagerly subscribe? This code is inefficient.
     */
    private static Observable<Boolean> subscribeToUpdateResult(Observable<MultiSourcedDataHolder.Status> status) {
        final AsyncSubject<Boolean> result = AsyncSubject.create();  // use an async subject as we only need the last result
        status.subscribe(new Subscriber<MultiSourcedDataHolder.Status>() {
            @Override
            public void onCompleted() {
                result.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Registry update failure", e);
                result.onError(e);
                e.printStackTrace();
            }

            @Override
            public void onNext(MultiSourcedDataHolder.Status status) {
                logger.debug("Registry updated completed with status {}", status);
                if (status.equals(MultiSourcedDataHolder.Status.AddedFirst)
                        || status.equals(MultiSourcedDataHolder.Status.RemovedLast)) {
                    result.onNext(true);
                } else {
                    result.onNext(false);
                }
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
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest, final Source.Matcher sourceMatcher) {
        return forSnapshot(interest).filter(new Func1<InstanceInfo, Boolean>() {
            @Override
            public Boolean call(InstanceInfo instanceInfo) {
                MultiSourcedDataHolder<InstanceInfo> holder = internalStore.get(instanceInfo.getId());
                return holder != null && sourceMatcher.match(holder.getSource());
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
            // It is possible that the same instanceinfo will be both in snapshot and paused notification queue.
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
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Source.Matcher sourceMatcher) {
        return forInterest(interest).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                if (changeNotification instanceof Sourced) {
                    Source notificationSource = ((Sourced) changeNotification).getSource();
                    return sourceMatcher.match(notificationSource);
                } else {
                    logger.warn("Received notification without a source, {}", changeNotification);
                    return false;
                }
            }
        });
    }

    @Override
    public Observable<Long> evictAll() {
        return getHolders()
                .switchMap(new Func1<MultiSourcedDataHolder<InstanceInfo>, Observable<MultiSourcedDataHolder.Status>>() {
                    @Override
                    public Observable<MultiSourcedDataHolder.Status> call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        for (Source source : holder.getAllSources()) {
                            holder.remove(source);
                        }
                        return null;
                    }
                })
                .countLong()
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
                });
    }

    @Override
    public Observable<Long> evictAll(final Source source) {
        return getHolders()
                .switchMap(new Func1<MultiSourcedDataHolder<InstanceInfo>, Observable<MultiSourcedDataHolder.Status>>() {
                    @Override
                    public Observable<MultiSourcedDataHolder.Status> call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        return holder.remove(source);
                    }
                })
                .countLong()
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
                });
    }
    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        return Observable.from(internalStore.values());
    }

    @Override
    public Observable<Void> shutdown() {
        invoker.shutdown();
        notificationSubject.onCompleted();
        internalStore.clear();
        return indexRegistry.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        invoker.shutdown();
        notificationSubject.onCompleted();
        return indexRegistry.shutdown(cause);
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
